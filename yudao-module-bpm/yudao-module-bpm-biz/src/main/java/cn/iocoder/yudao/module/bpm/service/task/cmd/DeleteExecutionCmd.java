package cn.iocoder.yudao.module.bpm.service.task.cmd;


import org.flowable.common.engine.api.FlowableException;
import org.flowable.common.engine.impl.interceptor.Command;
import org.flowable.common.engine.impl.interceptor.CommandContext;
import org.flowable.engine.impl.persistence.entity.ExecutionEntity;
import org.flowable.engine.impl.persistence.entity.ExecutionEntityManager;
import org.flowable.engine.impl.util.CommandContextUtil;

public class DeleteExecutionCmd implements Command<Void> {

    private final String executionId;
    private final String deleteReason;

    public DeleteExecutionCmd(String executionId, String deleteReason) {
        this.executionId = executionId;
        this.deleteReason = deleteReason;
    }

    @Override
    public Void execute(CommandContext commandContext) {
        // 正确获取 ExecutionEntityManager
        ExecutionEntityManager executionEntityManager = CommandContextUtil.getExecutionEntityManager(commandContext);
        ExecutionEntity execution = executionEntityManager.findById(executionId);
        if (execution == null) {
            throw new FlowableException("Execution not found for id: " + executionId);
        }
        executionEntityManager.deleteChildExecutions(execution, deleteReason, false);
        executionEntityManager.deleteExecutionAndRelatedData(execution, deleteReason, false);
        return null;
    }
}