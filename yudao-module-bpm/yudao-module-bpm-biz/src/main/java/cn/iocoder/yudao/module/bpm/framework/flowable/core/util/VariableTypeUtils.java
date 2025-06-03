package cn.iocoder.yudao.module.bpm.framework.flowable.core.util;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.NumberUtil;

import java.util.List;

/**
 * 变量类型转换工具类
 */
public class VariableTypeUtils {

    /**
     * 根据变量的类型，转换参数的值
     *
     * @param variable   变量的值
     * @param paramValue 参数值
     * @return 转换后的值
     */
    public static Object convertByType(Object variable, Object paramValue) {
        if (variable == null || paramValue == null) {
            return false;
        }
        // 如果变量是列表类型，获取列表的第一个元素作为类型
        if (variable instanceof List<?> list) {
            if (!CollUtil.isEmpty(list)) {
                Object firstElement = list.get(0);
                return convertParamValueByType(firstElement, paramValue);
            }
        } else {
            // 非列表类型，直接根据变量的类型转换参数值
            return convertParamValueByType(variable, paramValue);
        }
        return paramValue;
    }

    /**
     * 根据变量的类型，转换参数的值
     *
     * @param variableType 变量的类型
     * @param paramValue   参数值
     * @return 转换后的值
     */
    private static Object convertParamValueByType(Object variableType, Object paramValue) {
        if (variableType == null || paramValue == null) {
            return paramValue;
        }
        // 如果变量是数值类型，将参数值转换为对应的数值类型
        if (variableType instanceof Number) {
            return convertNumberParamValue(variableType, paramValue);
        }
        // 如果变量是布尔类型，将参数值转换为布尔值
        else if (variableType instanceof Boolean) {
            return Boolean.parseBoolean(paramValue.toString());
        }
        // 如果变量是字符串类型，尝试将其转换为数值类型
        else if (variableType instanceof String variableTypeStr) {
            // 如果变量值本身是数值类型的字符串（如 "501" 或 "501.5"），则将 paramValue 转换为对应的数值类型
            if (NumberUtil.isNumber(variableTypeStr)) {
                // 根据变量值是否为小数，决定转换为 Double 或 Integer
                if (variableTypeStr.contains(".")) {
                    return convertNumberParamValue(Double.parseDouble(variableTypeStr), paramValue);
                } else {
                    return convertNumberParamValue(Integer.parseInt(variableTypeStr), paramValue);
                }
            }
            // 如果变量值不是数值类型的字符串，返回字符串形式
            return paramValue.toString();
        }
        // 其他类型直接返回原值
        return paramValue;
    }

    /**
     * 处理数值类型的参数值转换
     *
     * @param variableType 变量的类型
     * @param paramValue   参数值
     * @return 转换后的数值
     */
    private static Object convertNumberParamValue(Object variableType, Object paramValue) {
        try {
            String paramStr = paramValue.toString();
            // 确保 paramStr 是有效的数值
            if (NumberUtil.isNumber(paramStr)) {
                // 根据变量类型将参数值转换为对应的数值类型
                if (variableType instanceof Integer) {
                    return Integer.parseInt(paramStr);
                } else if (variableType instanceof Long) {
                    return Long.parseLong(paramStr);
                } else if (variableType instanceof Double) {
                    return Double.parseDouble(paramStr);
                } else if (variableType instanceof Float) {
                    return Float.parseFloat(paramStr);
                } else if (variableType instanceof Short) {
                    return Short.parseShort(paramStr);
                } else if (variableType instanceof Byte) {
                    return Byte.parseByte(paramStr);
                }
            }
        } catch (NumberFormatException e) {
            // 转换失败保持原样
        }
        return paramValue;
    }

    /**
     * 为列表类型转换参数值
     *
     * @param list       列表
     * @param paramValue 参数值
     * @return 转换后的值
     */
    public static Object convertForList(List<?> list, Object paramValue) {
        if (list == null || list.isEmpty() || paramValue == null) {
            return paramValue; // 返回原值，或根据需求返回默认值
        }

        // 获取列表的第一个元素作为类型依据
        Object firstElement = list.get(0);
        if (firstElement == null) {
            return paramValue; // 如果列表元素是 null，返回原值
        }
        // 根据列表元素类型转换参数值
        if (firstElement instanceof String) {
            return paramValue.toString(); // 如果列表元素是字符串，将参数值转换为字符串
        } else if (firstElement instanceof Number) {
            return convertNumberParamValue(firstElement, paramValue); // 如果列表元素是数值，将参数值转换为数值
        } else if (firstElement instanceof Boolean) {
            return Boolean.parseBoolean(paramValue.toString()); // 如果列表元素是布尔值，将参数值转换为布尔值
        }

        return paramValue; // 其他类型直接返回原值
    }

}
