package demo;


import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.engine.impl.util.CollectionUtil;
import org.activiti.engine.runtime.Execution;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Task;
import org.activiti.engine.test.ActivitiRule;
import org.activiti.engine.test.Deployment;
import org.activiti.engine.test.mock.NoOpServiceTasks;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

public class FirstTest {
	
	// This is just the very first step to get the stuff going: starts a process
    // engine (NOT in Spring yet), deploys the process to H2 db in mem,
	// and starts the process, checking bits here and there
	
	
	//TODO: The rule should work without needing an activiti.cfg.xml!
	// Will update Activiti code to allow this!
	@Rule
	public ActivitiRule activitiRule = new ActivitiRule();
	
	@Test
	@Deployment(resources="webinar-process.bpmn20.xml")
//	@MockServiceTask(id="sendPhotoTask", mockedClassName="org.activiti.standalone.testing.helpers.ServiceTaskTestMock") // TODO: if not set, the mockedClassName should be default. Needs change in Activiti test core
	@NoOpServiceTasks
	public void theAwesomeTest() {
		
		// Prep some services
		RepositoryService repositoryService = activitiRule.getRepositoryService();
		RuntimeService runtimeService = activitiRule.getRuntimeService();
		TaskService taskService = activitiRule.getTaskService();
		
		// Check the process definition
		Assert.assertEquals(1, repositoryService.createProcessDefinitionQuery().count());
		
		// Start the process instance
		Map<String, Object> variables = new HashMap<String, Object>();
		variables.put("photos", Arrays.asList("link1", "link2", "link3"));
		ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("webinarprocess", variables);
		Assert.assertEquals(1, runtimeService.createProcessInstanceQuery().count());
		
		// Since we've got 3 photo's in there, should have 3 wait states (the service task has been mocked)
		List<Execution> waitingExecutions = runtimeService.createExecutionQuery().activityId("wait").list();
		Assert.assertEquals(3, waitingExecutions.size());
		
		// triggering those (mimicing JMS callback) completed the multi instance, leading a the review user task
		for (Execution execution : waitingExecutions) {
			Assert.assertEquals(0, taskService.createTaskQuery().count());
			runtimeService.signal(execution.getId());
		}
		
		// Now, the review task should exist for the reviewers group
		Task task = taskService.createTaskQuery().taskCandidateGroup("reviewers").singleResult();
		Assert.assertNotNull(task);
		Assert.assertEquals("Review results", task.getName());
		
		// Complete the task by setting it to approved
		taskService.complete(task.getId(), CollectionUtil.singletonMap("approved", true));
		
		// process should be ended
		Assert.assertEquals(0, runtimeService.createProcessInstanceQuery().count());
		
	}

}
