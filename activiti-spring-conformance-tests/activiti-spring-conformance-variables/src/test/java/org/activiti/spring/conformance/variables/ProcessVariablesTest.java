package org.activiti.spring.conformance.variables;

import org.activiti.api.model.shared.event.RuntimeEvent;
import org.activiti.api.model.shared.event.VariableEvent;
import org.activiti.api.model.shared.model.VariableInstance;
import org.activiti.api.process.model.ProcessInstance;
import org.activiti.api.process.model.builders.GetVariablesPayloadBuilder;
import org.activiti.api.process.model.builders.ProcessPayloadBuilder;
import org.activiti.api.process.model.builders.SetVariablesPayloadBuilder;
import org.activiti.api.process.model.events.BPMNActivityEvent;
import org.activiti.api.process.model.events.BPMNSequenceFlowTakenEvent;
import org.activiti.api.process.model.events.ProcessRuntimeEvent;
import org.activiti.api.process.runtime.ProcessAdminRuntime;
import org.activiti.api.process.runtime.ProcessRuntime;
import org.activiti.api.runtime.shared.query.Page;
import org.activiti.api.runtime.shared.query.Pageable;
import org.activiti.api.task.model.events.TaskRuntimeEvent;
import org.activiti.spring.conformance.util.security.SecurityUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.activiti.spring.conformance.variables.VariablesRuntimeTestConfiguration.collectedEvents;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Java6Assertions.tuple;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class ProcessVariablesTest {

    private final String processKey = "usertaskas-b5300a4b-8950-4486-ba20-a8d775a3d75d";

    @Autowired
    private ProcessRuntime processRuntime;

    @Autowired
    private SecurityUtil securityUtil;

    @Autowired
    private ProcessAdminRuntime processAdminRuntime;

    private String processInstanceId;

    private List<VariableInstance> variableInstanceList;

    @Before
    public void cleanUp() {
        collectedEvents.clear();
    }

    @Test
    public void shouldGetSameNamesAndValues() {

        securityUtil.logInAs("user1");

        startProcess();

        setVariables();

        assertThat(collectedEvents)
                .extracting("eventType","entity.name","entity.value")
                .containsExactly(
                        tuple(  VariableEvent.VariableEvents.VARIABLE_CREATED,
                                variableInstanceList.get(0).getName(),
                                variableInstanceList.get(0).getValue()),
                        tuple(  VariableEvent.VariableEvents.VARIABLE_CREATED,
                                variableInstanceList.get(1).getName(),
                                variableInstanceList.get(1).getValue())
                );
    }

    @Test
    public void shouldGetProcessIdAndNotTaskId() {

        securityUtil.logInAs("user1");

        startProcess();

        setVariables();

        VariableInstance variableOneRuntime = variableInstanceList.get(0);
        assertThat(variableOneRuntime.getProcessInstanceId()).isEqualTo(processInstanceId);
        assertThat(variableOneRuntime.getTaskId()).isNull();

        assertThat(collectedEvents)
                .extracting("eventType","entity.name","entity.value")
                .containsExactly(
                        tuple(  VariableEvent.VariableEvents.VARIABLE_CREATED,
                                variableInstanceList.get(0).getName(),
                                variableInstanceList.get(0).getValue()),
                        tuple(  VariableEvent.VariableEvents.VARIABLE_CREATED,
                                variableInstanceList.get(1).getName(),
                                variableInstanceList.get(1).getValue())
                );
    }

    @Test
    public void shouldNotBeTaskVariable() {
        securityUtil.logInAs("user1");

        startProcess();

        setVariables();

        VariableInstance variableOneRuntime = variableInstanceList.get(0);
        assertThat(variableOneRuntime.isTaskVariable()).isFalse();

        assertThat(collectedEvents)
                .extracting("eventType","entity.name","entity.value")
                .containsExactly(
                        tuple(  VariableEvent.VariableEvents.VARIABLE_CREATED,
                                variableInstanceList.get(0).getName(),
                                variableInstanceList.get(0).getValue()),
                        tuple(  VariableEvent.VariableEvents.VARIABLE_CREATED,
                                variableInstanceList.get(1).getName(),
                                variableInstanceList.get(1).getValue())
                );
    }

    @Test
    public void shouldGetRightVariableType(){
        securityUtil.logInAs("user1");

        startProcess();

        setVariables();

        VariableInstance variableOneRuntime = variableInstanceList.get(0);
        VariableInstance variableTwoRuntime = variableInstanceList.get(1);
        assertThat(variableOneRuntime.getType()).isEqualTo("string");
        assertThat(variableTwoRuntime.getType()).isEqualTo("integer");

        assertThat(collectedEvents)
                .extracting("eventType","entity.name","entity.value")
                .containsExactly(
                        tuple(  VariableEvent.VariableEvents.VARIABLE_CREATED,
                                variableInstanceList.get(0).getName(),
                                variableInstanceList.get(0).getValue()),
                        tuple(  VariableEvent.VariableEvents.VARIABLE_CREATED,
                                variableInstanceList.get(1).getName(),
                                variableInstanceList.get(1).getValue())
                );
    }

    @After
    public void cleanup() {
        securityUtil.logInAs("admin");
        Page<ProcessInstance> processInstancePage = processAdminRuntime.processInstances(Pageable.of(0, 50));
        for (ProcessInstance pi : processInstancePage.getContent()) {
            processAdminRuntime.delete(ProcessPayloadBuilder.delete(pi.getId()));
        }
    }

    private void startProcess(){
        processInstanceId = processRuntime.start(ProcessPayloadBuilder
                .start()
                .withProcessDefinitionKey(processKey)
                .withBusinessKey("my-business-key")
                .withName("my-process-instance-name")
                .build()).getId();

        assertThat(collectedEvents)
                .extracting(RuntimeEvent::getEventType)
                .containsExactly(
                        ProcessRuntimeEvent.ProcessEvents.PROCESS_CREATED,
                        ProcessRuntimeEvent.ProcessEvents.PROCESS_STARTED,
                        BPMNActivityEvent.ActivityEvents.ACTIVITY_STARTED,
                        BPMNActivityEvent.ActivityEvents.ACTIVITY_COMPLETED,
                        BPMNSequenceFlowTakenEvent.SequenceFlowEvents.SEQUENCE_FLOW_TAKEN,
                        BPMNActivityEvent.ActivityEvents.ACTIVITY_STARTED,
                        TaskRuntimeEvent.TaskEvents.TASK_CREATED,
                        TaskRuntimeEvent.TaskEvents.TASK_ASSIGNED
                );

        collectedEvents.clear();
    }

    private void setVariables(){

        Map<String, Object> variablesMap = new HashMap<>();
        variablesMap.put("one", "variableOne");
        variablesMap.put("two", 2);

        processRuntime.setVariables(new SetVariablesPayloadBuilder().withVariables(variablesMap).withProcessInstanceId(processInstanceId).build());

        variableInstanceList = processRuntime.variables(new GetVariablesPayloadBuilder().withProcessInstanceId(processInstanceId).build());
    }

}
