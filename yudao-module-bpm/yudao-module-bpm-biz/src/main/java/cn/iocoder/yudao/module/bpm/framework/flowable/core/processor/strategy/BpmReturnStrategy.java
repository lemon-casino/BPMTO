package cn.iocoder.yudao.module.bpm.framework.flowable.core.processor.strategy;

import cn.iocoder.yudao.module.bpm.framework.flowable.core.processor.context.BpmReturnContext;

/**
 * BPM 任务退回策略接口
 * 
 * 不同类型的节点需要不同的退回处理策略
 * 
 * @author 芋道源码
 */
public interface BpmReturnStrategy {

    /**
     * 计算退回范围
     * 
     * 根据目标节点类型和当前流程状态，计算需要退回的任务和执行流
     * 
     * @param context 退回上下文
     */
    void calculateReturnScope(BpmReturnContext context);

    /**
     * 验证退回的可行性
     * 
     * @param context 退回上下文
     * @return 是否可以退回
     */
    boolean validateReturn(BpmReturnContext context);

    /**
     * 获取策略支持的节点类型
     * 
     * @return 支持的节点类型
     */
    Class<?> getSupportedNodeType();
}
