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
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = Application.class)
public class ApplicationTests {
	
	@Autowired
	private RepositoryService repositoryService;
	
	@Autowired
	private RuntimeService runtimeService;
	
	@Autowired
	private TaskService taskService;

	@Test
	public void contextLoads() {
		
		// Check the process definition
		Assert.assertEquals(1, repositoryService.createProcessDefinitionQuery().count());
		
		// send a photo to REST service
		// Start the process instance
		Map<String, Object> variables = new HashMap<String, Object>();
		variables.put("photos", Arrays.asList(1L, 2L, 3L));
		ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("dogeProcess", variables);
		Assert.assertEquals(1, runtimeService.createProcessInstanceQuery().count());
		
		// can this be sent to a Spring Integration processor? 
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
