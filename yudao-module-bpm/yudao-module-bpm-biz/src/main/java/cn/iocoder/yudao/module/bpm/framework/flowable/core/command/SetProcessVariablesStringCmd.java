package cn.iocoder.yudao.module.bpm.framework.flowable.core.command;

import lombok.extern.slf4j.Slf4j;
import org.flowable.common.engine.impl.interceptor.Command;
import org.flowable.common.engine.impl.interceptor.CommandContext;
import org.flowable.engine.impl.persistence.entity.ExecutionEntity;
import org.flowable.engine.impl.persistence.entity.ExecutionEntityManager;
import org.flowable.engine.impl.util.CommandContextUtil;
import org.flowable.variable.api.persistence.entity.VariableInstance;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 设置流程变量的命令，强制以字符串形式保存所有变量
 */
@Slf4j
public class SetProcessVariablesStringCmd implements Command<Map<String, Object>>, Serializable {

    private static final long serialVersionUID = 1L;

    protected String executionId;
    protected Map<String, Object> variables;
    protected boolean isLocal;

    public SetProcessVariablesStringCmd(String executionId, Map<String, Object> variables, boolean isLocal) {
        this.executionId = executionId;
        this.variables = variables;
        this.isLocal = isLocal;
    }

    @Override
    public Map<String, Object> execute(CommandContext commandContext) {
        if (executionId == null) {
            log.error("[execute][执行ID为空]");
            throw new IllegalArgumentException("executionId is null");
        }

        // 获取执行实例
        ExecutionEntityManager executionEntityManager = CommandContextUtil.getExecutionEntityManager(commandContext);
        ExecutionEntity execution = executionEntityManager.findById(executionId);
        if (execution == null) {
            log.warn("[execute][执行实例({})不存在]", executionId);
            return null;
        }

        Map<String, Object> result = new HashMap<>();
        try {
            // 强制将所有值转换为字符串
            variables.forEach((key, value) -> {
                if (value != null) {
                    // 对于所有值，都先检查现有变量
                    VariableInstance existingVar = isLocal ? execution.getVariableInstanceLocal(key) : execution.getVariableInstance(key);

                    // 日志记录原变量类型和值
                    if (existingVar != null) {
                        log.info("[execute][更新变量] 变量名: {}, 原值: {}({}), 新值: {}({})",
                                key, existingVar.getValue(), existingVar.getTypeName(),
                                value, value.getClass().getSimpleName());
                    } else {
                        log.info("[execute][创建变量] 变量名: {}, 值: {}({})",
                                key, value, value.getClass().getSimpleName());
                    }

                    try {
                        // 处理变量设置
                        if (isLocal) {
                            // 确保字符串转换
                            String stringValue = value.toString();
                            execution.setVariableLocal(key, stringValue);
                            log.info("[execute][设置本地变量成功] 变量名: {}, 值: {}", key, stringValue);
                        } else {
                            // 确保字符串转换
                            String stringValue = value.toString();
                            execution.setVariable(key, stringValue);
                            log.info("[execute][设置全局变量成功] 变量名: {}, 值: {}", key, stringValue);
                        }

                        // 验证变量是否正确设置
                        VariableInstance updatedVar = isLocal ? execution.getVariableInstanceLocal(key) : execution.getVariableInstance(key);
                        if (updatedVar != null) {
                            log.info("[execute][验证变量] 变量名: {}, 值: {}({})",
                                    key, updatedVar.getValue(), updatedVar.getTypeName());
                            result.put(key, updatedVar.getValue());
                        } else {
                            log.warn("[execute][变量设置后未找到] 变量名: {}", key);
                        }
                    } catch (Exception e) {
                        log.error("[execute][设置变量({})异常]", key, e);
                    }
                } else {
                    // 对于null值，正常处理
                    try {
                        if (isLocal) {
                            execution.setVariableLocal(key, null);
                            log.info("[execute][设置本地变量为null成功] 变量名: {}", key);
                        } else {
                            execution.setVariable(key, null);
                            log.info("[execute][设置全局变量为null成功] 变量名: {}", key);
                        }
                        result.put(key, null);
                    } catch (Exception e) {
                        log.error("[execute][设置变量({})为null异常]", key, e);
                    }
                }
            });
            
            // 刷新变量
            execution.forceUpdate();
            log.info("[execute][强制更新执行实例变量完成] executionId: {}", executionId);
            
            return result;
        } catch (Exception e) {
            log.error("[execute][设置流程变量异常] executionId: {}", executionId, e);
            throw e;
        }
    }
} 