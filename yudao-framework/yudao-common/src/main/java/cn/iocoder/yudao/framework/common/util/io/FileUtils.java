package cn.iocoder.yudao.framework.common.util.io;

import cn.hutool.core.io.FileTypeUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.FileNameUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import lombok.SneakyThrows;

import java.io.ByteArrayInputStream;
import java.io.File;

/**
 * 文件工具类
 *
 * @author 芋道源码
 */
public class FileUtils {

    /**
     * 创建临时文件
     * 该文件会在 JVM 退出时，进行删除
     *
     * @param data 文件内容
     * @return 文件
     */
    @SneakyThrows
    public static File createTempFile(String data) {
        File file = createTempFile();
        // 写入内容
        FileUtil.writeUtf8String(data, file);
        return file;
    }

    /**
     * 创建临时文件
     * 该文件会在 JVM 退出时，进行删除
     *
     * @param data 文件内容
     * @return 文件
     */
    @SneakyThrows
    public static File createTempFile(byte[] data) {
        File file = createTempFile();
        // 写入内容
        FileUtil.writeBytes(data, file);
        return file;
    }

    /**
     * 创建临时文件，无内容
     * 该文件会在 JVM 退出时，进行删除
     *
     * @return 文件
     */
    @SneakyThrows
    public static File createTempFile() {
        // 创建文件，通过 UUID 保证唯一
        File file = File.createTempFile(IdUtil.simpleUUID(), null);
        // 标记 JVM 退出时，自动删除
        file.deleteOnExit();
        return file;
    }

    /**
     * 生成文件路径
     *
     * @param content      文件内容
     * @param originalName 原始文件名
     * @return path，唯一不可重复
     */
    public static String generatePath(byte[] content, String originalName) {
        // 使用时间戳确保唯一性
        String timestamp = String.valueOf(System.currentTimeMillis());
        
        // 情况一：如果存在原始文件名，则使用 原始文件名-时间戳.扩展名 格式
        if (StrUtil.isNotBlank(originalName)) {
            // 提取文件名（不含扩展名）
            String fileName = FileNameUtil.mainName(originalName);
            // 提取扩展名
            String extName = FileNameUtil.extName(originalName);
            // 生成 文件名-时间戳.扩展名 格式
            return StrUtil.isBlank(extName) ? 
                    fileName + "-" + timestamp : 
                    fileName + "-" + timestamp + "." + extName;
        }
        
        // 情况二：没有原始文件名时，使用时间戳和内容类型
        String fileType = FileTypeUtil.getType(new ByteArrayInputStream(content));
        return timestamp + '.' + fileType;
    }

}
