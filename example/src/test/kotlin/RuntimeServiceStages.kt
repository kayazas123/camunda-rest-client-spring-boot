package org.camunda.bpm.extension.feign.itest

import com.tngtech.jgiven.annotation.IsTag
import com.tngtech.jgiven.annotation.ProvidedScenarioState
import com.tngtech.jgiven.annotation.ScenarioState
import com.tngtech.jgiven.integration.spring.JGivenStage
import org.assertj.core.api.Assertions.assertThat
import org.camunda.bpm.engine.RepositoryService
import org.camunda.bpm.engine.RuntimeService
import org.camunda.bpm.engine.repository.ProcessDefinition
import org.camunda.bpm.engine.runtime.ProcessInstance
import org.camunda.bpm.model.bpmn.Bpmn
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier

@JGivenStage
class RuntimeServiceActionStage : ActionStage<RuntimeServiceActionStage, RuntimeService>() {

  @Autowired
  @ProvidedScenarioState
  lateinit var repositoryService: RepositoryService

  @Autowired
  @Qualifier("remote")
  @ProvidedScenarioState(resolution = ScenarioState.Resolution.NAME)
  override lateinit var remoteService: RuntimeService

  @Autowired
  @Qualifier("runtimeService")
  @ProvidedScenarioState(resolution = ScenarioState.Resolution.NAME)
  override lateinit var localService: RuntimeService

  @ProvidedScenarioState(resolution = ScenarioState.Resolution.TYPE)
  lateinit var processDefinition: ProcessDefinition

  fun process_with_user_task_is_deployed(
    processDefinitionKey: String = "process_with_user_task",
    userTaskId: String = "user_task"
  ): RuntimeServiceActionStage {

    val instance = Bpmn
      .createExecutableProcess(processDefinitionKey)
      .startEvent("start")
      .camundaAsyncAfter(true)
      .userTask(userTaskId)
      .endEvent("end")
      .done()

    val deployment = repositoryService
      .createDeployment()
      .addModelInstance("$processDefinitionKey.bpmn", instance)
      .name("process_with_user_task")
      .deploy()

    processDefinition = repositoryService
      .createProcessDefinitionQuery()
      .deploymentId(deployment.id)
      .singleResult()

    return self()
  }

  fun process_with_intermediate_message_catch_event_is_deployed(
    processDefinitionKey: String = "process_with_message_catch_event",
    userTaskId: String = "user-task",
    messageName: String = "my-message"
  ): RuntimeServiceActionStage {

    val instance = Bpmn
      .createExecutableProcess(processDefinitionKey)
      .startEvent("start")
      .intermediateCatchEvent().message(messageName)
      .userTask(userTaskId)
      .endEvent("end")
      .done()

    val deployment = repositoryService
      .createDeployment()
      .addModelInstance("$processDefinitionKey.bpmn", instance)
      .name("process_with_message_catch_event")
      .deploy()

    processDefinition = repositoryService
      .createProcessDefinitionQuery()
      .deploymentId(deployment.id)
      .singleResult()

    return self()
  }

  fun process_with_start_by_message_event_is_deployed(
    processDefinitionKey: String = "process_start_message",
    userTaskId: String = "user-task",
    messageName: String = "my-message"
  ): RuntimeServiceActionStage {

    val instance = Bpmn
      .createExecutableProcess(processDefinitionKey)
      .startEvent()
      .message(messageName)
      .userTask(userTaskId)
      .endEvent("end")
      .done()

    val deployment = repositoryService
      .createDeployment()
      .addModelInstance("$processDefinitionKey.bpmn", instance)
      .name("process_start_message")
      .deploy()

    processDefinition = repositoryService
      .createProcessDefinitionQuery()
      .deploymentId(deployment.id)
      .singleResult()

    return self()
  }


  fun process_is_started_by_key(
    processDefinitionKey: String,
    businessKey: String? = null,
    caseInstanceId: String? = null,
    variables: Map<String, Any>? = null
  ): RuntimeServiceActionStage {

    val processInstance = if (variables != null && businessKey != null && caseInstanceId != null) {
      localService.startProcessInstanceByKey(processDefinitionKey, businessKey, caseInstanceId, variables)
    } else if (businessKey != null && caseInstanceId != null) {
      localService.startProcessInstanceByKey(processDefinitionKey, businessKey, caseInstanceId)
    } else if (businessKey != null) {
      localService.startProcessInstanceByKey(processDefinitionKey, businessKey)
    } else {
      localService.startProcessInstanceByKey(processDefinitionKey)
    }

    // started instance
    assertThat(processInstance).isNotNull
    // waits in message event
    assertThat(localService
      .createProcessInstanceQuery()
      .processInstanceId(processInstance.id)
      .singleResult()).isNotNull

    return self()
  }

}

@JGivenStage
class RuntimeServiceAssertStage : AssertStage<RuntimeServiceAssertStage, RuntimeService>() {

  @Autowired
  @Qualifier("runtimeService")
  @ProvidedScenarioState(resolution = ScenarioState.Resolution.NAME)
  override lateinit var localService: RuntimeService

  @ProvidedScenarioState
  var processInstance: ProcessInstance? = null

  fun process_instance_exists(
    processDefinitionKey: String? = null,
    processDefinitionId: String? = null,
    containingSimpleProcessVariables: Map<String, Any>? = null,
    processInstanceAssertions: (ProcessInstance, AssertStage<*, RuntimeService>) -> Unit = { _, _ -> }
  ): RuntimeServiceAssertStage {

    val query = localService.createProcessInstanceQuery().apply {
      if (processDefinitionId != null) {
        this.processDefinitionId(processDefinitionId)
      }
      if (processDefinitionKey != null) {
        this.processDefinitionKey(processDefinitionKey)
      }
      if (containingSimpleProcessVariables != null) {
        containingSimpleProcessVariables.entries.forEach {
          this.variableValueEquals(it.key, it.value)
        }
      }
    }
    val instances = query.list()
    assertThat(instances.size).`as`("expect to find exactly 1 instance", processDefinitionKey).isEqualTo(1)
    processInstance = instances[0]
    assertThat(processInstance).isNotNull
    processInstanceAssertions(processInstance!!, this)
    return self()
  }

  fun subscription_exists(messageName: String): RuntimeServiceAssertStage {

    assertThat(localService
      .createEventSubscriptionQuery()
      .eventType("message")
      .eventName(messageName)
      .singleResult()
    ).isNotNull

    return self()
  }
}

@IsTag(name = "RuntimeService")
annotation class RuntimeServiceCategory

