package cn.iocoder.yudao.module.bpm.framework.flowable.core.el;

import org.flowable.common.engine.api.variable.VariableContainer;
import org.flowable.common.engine.impl.el.function.AbstractFlowableVariableExpressionFunction;
import org.springframework.stereotype.Component;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.VariableTypeUtils;

import java.util.List;

@Component
public class ContainsExpressionFunction extends AbstractFlowableVariableExpressionFunction {

    public ContainsExpressionFunction() {
        super("contains");
    }

    /**
     * 检查列表中是否包含转换后的参数值
     *
     * @param variableContainer 变量的容器
     * @param variableName      变量名
     * @param paramValue        参数值
     * @return 是否包含
     */
    public static boolean contains(VariableContainer variableContainer, String variableName, Object paramValue) {
        // 检查参数是否为空
        if (variableContainer == null || variableName == null || paramValue == null) {
            return false;
        }

        // 获取变量值
        Object variable = variableContainer.getVariable(variableName);
        if (!(variable instanceof List<?> list) || list.isEmpty()) {
            return false; // 变量不存在或变量类型不是列表
        }

        // 转换参数值并检查是否包含
        Object convertedValue = VariableTypeUtils.convertForList(list, paramValue);
        return list.contains(convertedValue);
    }
}
