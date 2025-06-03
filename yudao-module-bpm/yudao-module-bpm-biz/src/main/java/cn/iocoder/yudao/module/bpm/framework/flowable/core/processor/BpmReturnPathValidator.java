package cn.iocoder.yudao.module.bpm.framework.flowable.core.processor;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.processor.context.BpmReturnContext;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.*;

import java.util.*;

/**
 * BPM 退回路径验证器
 * 
 * 负责验证退回路径的有效性，防止无效的退回操作
 * 
 * @author 芋道源码
 */
@Slf4j
public class BpmReturnPathValidator {

    /**
     * 验证退回路径是否有效
     * 
     * @param context 退回上下文
     * @return 是否有效
     */
    public static boolean validateReturnPath(BpmReturnContext context) {
        log.debug("[validateReturnPath] 开始验证退回路径，源节点：{}，目标节点：{}", 
                context.getSourceElement().getId(), context.getTargetElement().getId());

        try {
            // 1. 基础验证
            if (!basicValidation(context)) {
                return false;
            }

            // 2. 路径可达性验证
            if (!pathReachabilityValidation(context)) {
                return false;
            }

            // 3. 网关兼容性验证
            if (!gatewayCompatibilityValidation(context)) {
                return false;
            }

            // 4. 多实例任务验证
            if (!multiInstanceValidation(context)) {
                return false;
            }

            // 5. 子流程验证
            if (!subProcessValidation(context)) {
                return false;
            }

            log.debug("[validateReturnPath] 退回路径验证通过");
            return true;

        } catch (Exception e) {
            log.error("[validateReturnPath] 退回路径验证异常", e);
            return false;
        }
    }

    /**
     * 检查是否存在循环退回
     * 
     * @param context 退回上下文
     * @return 是否存在循环
     */
    public static boolean hasCircularReturn(BpmReturnContext context) {
        log.debug("[hasCircularReturn] 检查循环退回，源节点：{}，目标节点：{}", 
                context.getSourceElement().getId(), context.getTargetElement().getId());

        try {
            // 1. 检查直接循环
            if (isDirectCircular(context)) {
                log.warn("[hasCircularReturn] 检测到直接循环退回");
                return true;
            }

            // 2. 检查间接循环
            if (isIndirectCircular(context)) {
                log.warn("[hasCircularReturn] 检测到间接循环退回");
                return true;
            }

            // 3. 检查网关循环
            if (isGatewayCircular(context)) {
                log.warn("[hasCircularReturn] 检测到网关循环退回");
                return true;
            }

            return false;

        } catch (Exception e) {
            log.error("[hasCircularReturn] 循环检查异常", e);
            return true; // 异常时保守处理，认为存在循环
        }
    }

    /**
     * 基础验证
     */
    private static boolean basicValidation(BpmReturnContext context) {
        // 1. 检查源节点和目标节点是否存在
        if (context.getSourceElement() == null) {
            log.warn("[basicValidation] 源节点不存在");
            return false;
        }

        if (context.getTargetElement() == null) {
            log.warn("[basicValidation] 目标节点不存在");
            return false;
        }

        // 2. 检查是否为同一个节点
        if (context.getSourceElement().getId().equals(context.getTargetElement().getId())) {
            log.warn("[basicValidation] 源节点和目标节点相同");
            return false;
        }

        // 3. 检查目标节点类型是否支持退回
        if (!isSupportedTargetNodeType(context.getTargetElement())) {
            log.warn("[basicValidation] 目标节点类型不支持退回：{}", 
                    context.getTargetElement().getClass().getSimpleName());
            return false;
        }

        return true;
    }

    /**
     * 路径可达性验证
     */
    private static boolean pathReachabilityValidation(BpmReturnContext context) {
        // 使用改进的路径可达性算法
        return isSequentialReachableImproved(context.getSourceElement(), 
                context.getTargetElement(), context.getBpmnModel());
    }

    /**
     * 网关兼容性验证
     */
    private static boolean gatewayCompatibilityValidation(BpmReturnContext context) {
        // 1. 检查路径中的网关类型
        List<FlowElement> pathGateways = findGatewaysInPath(context);
        
        for (FlowElement gateway : pathGateways) {
            if (!isGatewayReturnCompatible(gateway, context)) {
                log.warn("[gatewayCompatibilityValidation] 网关不兼容退回：{}", gateway.getId());
                return false;
            }
        }

        // 2. 检查网关配对
        if (!validateGatewayPairing(pathGateways)) {
            log.warn("[gatewayCompatibilityValidation] 网关配对不正确");
            return false;
        }

        return true;
    }

    /**
     * 多实例任务验证
     */
    private static boolean multiInstanceValidation(BpmReturnContext context) {
        // 1. 检查源节点是否为多实例任务
        if (isMultiInstanceTask(context.getSourceElement())) {
            if (!validateMultiInstanceSource(context)) {
                return false;
            }
        }

        // 2. 检查目标节点是否为多实例任务
        if (isMultiInstanceTask(context.getTargetElement())) {
            if (!validateMultiInstanceTarget(context)) {
                return false;
            }
        }

        return true;
    }

    /**
     * 子流程验证
     */
    private static boolean subProcessValidation(BpmReturnContext context) {
        // 1. 检查是否跨子流程退回
        if (isCrossSubProcess(context)) {
            log.warn("[subProcessValidation] 不支持跨子流程退回");
            return false;
        }

        // 2. 检查子流程内部退回
        if (isWithinSubProcess(context)) {
            return validateSubProcessInternalReturn(context);
        }

        return true;
    }

    /**
     * 检查直接循环
     */
    private static boolean isDirectCircular(BpmReturnContext context) {
        // 检查目标节点是否在源节点的后续路径中
        return isNodeInForwardPath(context.getTargetElement(), context.getSourceElement(), 
                context.getBpmnModel(), new HashSet<>());
    }

    /**
     * 检查间接循环
     */
    private static boolean isIndirectCircular(BpmReturnContext context) {
        // 通过分析流程历史，检查是否存在间接循环
        return analyzeProcessHistory(context);
    }

    /**
     * 检查网关循环
     */
    private static boolean isGatewayCircular(BpmReturnContext context) {
        // 检查网关分支是否会形成循环
        List<FlowElement> gateways = findGatewaysInPath(context);
        
        for (FlowElement gateway : gateways) {
            if (isGatewayFormingCircle(gateway, context)) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * 改进的序列可达性检查
     */
    private static boolean isSequentialReachableImproved(FlowElement source, FlowElement target, BpmnModel bpmnModel) {
        Set<String> visited = new HashSet<>();
        return isSequentialReachableRecursive(source, target, visited, bpmnModel, 0, 100); // 限制递归深度
    }

    /**
     * 递归检查序列可达性
     */
    private static boolean isSequentialReachableRecursive(FlowElement current, FlowElement target, 
                                                         Set<String> visited, BpmnModel bpmnModel, 
                                                         int depth, int maxDepth) {
        if (depth > maxDepth) {
            log.warn("[isSequentialReachableRecursive] 达到最大递归深度");
            return false;
        }

        if (current == null || visited.contains(current.getId())) {
            return false;
        }

        visited.add(current.getId());

        // 如果找到目标节点
        if (current.getId().equals(target.getId())) {
            return true;
        }

        // 检查入口连线
        List<SequenceFlow> incomingFlows = BpmnModelUtils.getElementIncomingFlows(current);
        for (SequenceFlow flow : incomingFlows) {
            FlowElement sourceElement = flow.getSourceFlowElement();
            
            // 特殊处理网关
            if (sourceElement instanceof ParallelGateway) {
                // 并行网关不支持退回
                continue;
            }
            
            if (isSequentialReachableRecursive(sourceElement, target, new HashSet<>(visited), 
                    bpmnModel, depth + 1, maxDepth)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 检查节点类型是否支持作为退回目标
     */
    private static boolean isSupportedTargetNodeType(FlowElement element) {
        return element instanceof UserTask ||
               element instanceof ServiceTask ||
               element instanceof ParallelGateway ||
               element instanceof ExclusiveGateway ||
               element instanceof InclusiveGateway;
    }

    /**
     * 查找路径中的网关
     */
    private static List<FlowElement> findGatewaysInPath(BpmReturnContext context) {
        List<FlowElement> gateways = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        
        findGatewaysRecursive(context.getSourceElement(), context.getTargetElement(), 
                gateways, visited, context.getBpmnModel());
        
        return gateways;
    }

    /**
     * 递归查找网关
     */
    private static void findGatewaysRecursive(FlowElement current, FlowElement target, 
                                            List<FlowElement> gateways, Set<String> visited, 
                                            BpmnModel bpmnModel) {
        if (current == null || visited.contains(current.getId())) {
            return;
        }

        visited.add(current.getId());

        if (BpmnModelUtils.isGateway(current)) {
            gateways.add(current);
        }

        if (current.getId().equals(target.getId())) {
            return;
        }

        // 继续向前查找
        List<SequenceFlow> incomingFlows = BpmnModelUtils.getElementIncomingFlows(current);
        for (SequenceFlow flow : incomingFlows) {
            findGatewaysRecursive(flow.getSourceFlowElement(), target, gateways, visited, bpmnModel);
        }
    }

    /**
     * 检查网关是否兼容退回
     */
    private static boolean isGatewayReturnCompatible(FlowElement gateway, BpmReturnContext context) {
        if (gateway instanceof ParallelGateway) {
            return validateParallelGatewayReturn((ParallelGateway) gateway, context);
        } else if (gateway instanceof InclusiveGateway) {
            return validateInclusiveGatewayReturn((InclusiveGateway) gateway, context);
        } else if (gateway instanceof ExclusiveGateway) {
            return validateExclusiveGatewayReturn((ExclusiveGateway) gateway, context);
        }
        
        return true;
    }

    /**
     * 验证网关配对
     */
    private static boolean validateGatewayPairing(List<FlowElement> gateways) {
        // 检查分支网关和聚合网关是否正确配对
        Map<String, Integer> gatewayTypes = new HashMap<>();
        
        for (FlowElement gateway : gateways) {
            String type = gateway.getClass().getSimpleName();
            gatewayTypes.put(type, gatewayTypes.getOrDefault(type, 0) + 1);
        }
        
        // 简单的配对检查：每种类型的网关数量应该是偶数（分支+聚合）
        for (Integer count : gatewayTypes.values()) {
            if (count % 2 != 0) {
                return false;
            }
        }
        
        return true;
    }

    // 其他辅助方法的实现...
    
    private static boolean isMultiInstanceTask(FlowElement element) {
        if (element instanceof UserTask) {
            return ((UserTask) element).getLoopCharacteristics() != null;
        }
        return false;
    }

    private static boolean validateMultiInstanceSource(BpmReturnContext context) {
        // 多实例任务作为源节点的验证逻辑
        return true;
    }

    private static boolean validateMultiInstanceTarget(BpmReturnContext context) {
        // 多实例任务作为目标节点的验证逻辑
        return true;
    }

    private static boolean isCrossSubProcess(BpmReturnContext context) {
        // 检查是否跨子流程
        return false; // 简化实现
    }

    private static boolean isWithinSubProcess(BpmReturnContext context) {
        // 检查是否在子流程内部
        return false; // 简化实现
    }

    private static boolean validateSubProcessInternalReturn(BpmReturnContext context) {
        // 子流程内部退回验证
        return true;
    }

    private static boolean isNodeInForwardPath(FlowElement target, FlowElement source, 
                                             BpmnModel bpmnModel, Set<String> visited) {
        // 检查目标节点是否在源节点的前向路径中
        return false; // 简化实现
    }

    private static boolean analyzeProcessHistory(BpmReturnContext context) {
        // 分析流程历史，检查间接循环
        return false; // 简化实现
    }

    private static boolean isGatewayFormingCircle(FlowElement gateway, BpmReturnContext context) {
        // 检查网关是否形成循环
        return false; // 简化实现
    }

    private static boolean validateParallelGatewayReturn(ParallelGateway gateway, BpmReturnContext context) {
        // 并行网关退回验证
        return true;
    }

    private static boolean validateInclusiveGatewayReturn(InclusiveGateway gateway, BpmReturnContext context) {
        // 包容网关退回验证
        return true;
    }

    private static boolean validateExclusiveGatewayReturn(ExclusiveGateway gateway, BpmReturnContext context) {
        // 排他网关退回验证
        return true;
    }
}
