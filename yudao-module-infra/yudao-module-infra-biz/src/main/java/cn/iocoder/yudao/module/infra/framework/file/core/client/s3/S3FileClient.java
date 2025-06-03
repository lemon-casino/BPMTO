package cn.iocoder.yudao.module.infra.framework.file.core.client.s3;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.iocoder.yudao.module.infra.framework.file.core.client.AbstractFileClient;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.time.Duration;

/**
 * 基于 S3 协议的文件客户端，实现 MinIO、阿里云、腾讯云、七牛云、华为云等云服务
 * <p>
 * S3 协议的客户端，采用亚马逊提供的 software.amazon.awssdk.s3 库
 *
 * @author 芋道源码
 */
public class S3FileClient extends AbstractFileClient<S3FileClientConfig> {

    private S3Client client;
    private S3Presigner presigner;

    public S3FileClient(Long id, S3FileClientConfig config) {
        super(id, config);
    }

    @Override
    protected void doInit() {
        // 补全 domain
        if (StrUtil.isEmpty(config.getDomain())) {
            config.setDomain(buildDomain());
        }
        // 初始化客户端
        client = S3Client.builder()
                .credentialsProvider(buildCredentials())
                .endpointOverride(URI.create(config.getEndpoint()))
                .region(Region.US_EAST_1) // 使用默认区域，因为我们通常使用自定义域名
                // 添加 path style 访问，对 MinIO 尤其重要
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
        // 初始化预签名客户端
        presigner = S3Presigner.builder()
                .credentialsProvider(buildCredentials())
                .endpointOverride(URI.create(config.getEndpoint()))
                .region(Region.US_EAST_1)
                // 保持与 S3Client 一致的配置
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }

    /**
     * 基于 config 秘钥，构建 S3 客户端的认证信息
     *
     * @return S3 客户端的认证信息
     */
    private StaticCredentialsProvider buildCredentials() {
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(config.getAccessKey(), config.getAccessSecret()));
    }

    /**
     * 基于 bucket + endpoint 构建访问的 Domain 地址
     *
     * @return Domain 地址
     */
    private String buildDomain() {
        // 如果是MinIO（通过endpoint判断），直接使用endpoint
        if (isMinIOEndpoint(config.getEndpoint())) {
            return config.getEndpoint();
        }
        // 如果已经是 http 或者 https，则不进行拼接
        if (HttpUtil.isHttp(config.getEndpoint()) || HttpUtil.isHttps(config.getEndpoint())) {
            return StrUtil.format("{}/{}", config.getEndpoint(), config.getBucket());
        }
        // 其他云存储服务
        return StrUtil.format("https://{}.{}", config.getBucket(), config.getEndpoint());
    }

    private boolean isMinIOEndpoint(String endpoint) {
        return endpoint.contains(":") && !endpoint.contains("aliyuncs.com") 
            && !endpoint.contains("myqcloud.com") 
            && !endpoint.contains("qiniucs.com")
            && !endpoint.contains("volces.com");
    }

    @Override
    public String upload(byte[] content, String path, String type) throws Exception {
        // 上传对象
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(config.getBucket())
                .key(path)
                .contentType(type)
                .build();
        client.putObject(putObjectRequest, RequestBody.fromBytes(content));

        // 拼接返回路径 - 修正 MinIO 路径格式
        if (isMinIOEndpoint(config.getEndpoint())) {
            // MinIO 的路径应该是 endpoint/bucket/path 的格式
            return config.getDomain() + "/" + config.getBucket() + "/" + path;
        }
        // 其他 S3 兼容的服务
        return config.getDomain() + "/" + path;
    }

    @Override
    public void delete(String path) throws Exception {
        DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                .bucket(config.getBucket())
                .key(path)
                .build();
        client.deleteObject(deleteObjectRequest);
    }

    @Override
    public byte[] getContent(String path) throws Exception {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(config.getBucket())
                .key(path)
                .build();
        ResponseInputStream<GetObjectResponse> response = client.getObject(getObjectRequest);
        return IoUtil.readBytes(response);
    }

    @Override
    public FilePresignedUrlRespDTO getPresignedObjectUrl(String path) throws Exception {
        // 生成预签名 URL，有效期 10 分钟
        PresignedPutObjectRequest presignedRequest = presigner.presignPutObject(builder ->
                builder.signatureDuration(Duration.ofMinutes(10))
                        .putObjectRequest(por -> por
                                .bucket(config.getBucket())
                                .key(path)));
        
        return new FilePresignedUrlRespDTO(presignedRequest.url().toString(), config.getDomain() + "/" + path);
    }

}
