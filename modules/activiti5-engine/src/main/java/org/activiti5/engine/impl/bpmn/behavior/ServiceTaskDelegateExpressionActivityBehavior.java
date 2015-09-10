/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.activiti5.engine.impl.bpmn.behavior;

import java.util.List;

import org.activiti.engine.ActivitiException;
import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.delegate.Expression;
import org.activiti.engine.delegate.JavaDelegate;
import org.activiti.engine.impl.delegate.ActivityBehavior;
import org.activiti5.engine.ActivitiIllegalArgumentException;
import org.activiti5.engine.delegate.BpmnError;
import org.activiti5.engine.impl.bpmn.helper.ClassDelegate;
import org.activiti5.engine.impl.bpmn.helper.ErrorPropagation;
import org.activiti5.engine.impl.bpmn.helper.SkipExpressionUtil;
import org.activiti5.engine.impl.bpmn.parser.FieldDeclaration;
import org.activiti5.engine.impl.context.Context;
import org.activiti5.engine.impl.delegate.ActivityBehaviorInvocation;
import org.activiti5.engine.impl.delegate.JavaDelegateInvocation;
import org.activiti5.engine.impl.pvm.delegate.ActivityExecution;
import org.activiti5.engine.impl.pvm.delegate.SignallableActivityBehavior;


/**
 * {@link ActivityBehavior} used when 'delegateExpression' is used
 * for a serviceTask.
 * 
 * @author Joram Barrez
 * @author Josh Long
 * @author Slawomir Wojtasiak (Patch for ACT-1159)
 * @author Falko Menge
 */
public class ServiceTaskDelegateExpressionActivityBehavior extends TaskActivityBehavior {
  
  protected Expression expression;
  protected Expression skipExpression;
  private final List<FieldDeclaration> fieldDeclarations;

  public ServiceTaskDelegateExpressionActivityBehavior(Expression expression, Expression skipExpression, List<FieldDeclaration> fieldDeclarations) {
    this.expression = expression;
    this.skipExpression = skipExpression;
    this.fieldDeclarations = fieldDeclarations;
  }

  @Override
  public void signal(ActivityExecution execution, String signalName, Object signalData) throws Exception {
    Object delegate = expression.getValue(execution);
    if( delegate instanceof SignallableActivityBehavior){
      ClassDelegate.applyFieldDeclaration(fieldDeclarations, delegate);
      ((SignallableActivityBehavior) delegate).signal( execution , signalName , signalData);
    }
  }

	public void execute(DelegateExecution execution) {
	  ActivityExecution activityExecution = (ActivityExecution) execution;
    try {
      boolean isSkipExpressionEnabled = SkipExpressionUtil.isSkipExpressionEnabled(activityExecution, skipExpression); 
      if (!isSkipExpressionEnabled || 
              (isSkipExpressionEnabled && !SkipExpressionUtil.shouldSkipFlowElement(activityExecution, skipExpression))) {
        
        // Note: we can't cache the result of the expression, because the
        // execution can change: eg.
        // delegateExpression='${mySpringBeanFactory.randomSpringBean()}'
        Object delegate = expression.getValue(execution);
        ClassDelegate.applyFieldDeclaration(fieldDeclarations, delegate);

        if (delegate instanceof ActivityBehavior) {

          if(delegate instanceof AbstractBpmnActivityBehavior){
            ((AbstractBpmnActivityBehavior) delegate).setMultiInstanceActivityBehavior(getMultiInstanceActivityBehavior());
          }

          Context.getProcessEngineConfiguration().getDelegateInterceptor()
                  .handleInvocation(new ActivityBehaviorInvocation((ActivityBehavior) delegate, activityExecution));

        } else if (delegate instanceof JavaDelegate) {
          Context.getProcessEngineConfiguration().getDelegateInterceptor().handleInvocation(new JavaDelegateInvocation((JavaDelegate) delegate, execution));
          leave(activityExecution);

        } else {
          throw new ActivitiIllegalArgumentException("Delegate expression " + expression + " did neither resolve to an implementation of "
                  + ActivityBehavior.class + " nor " + JavaDelegate.class);
        }
      } else {
        leave(activityExecution);
      }
    } catch (Exception exc) {

      Throwable cause = exc;
      BpmnError error = null;
      while (cause != null) {
        if (cause instanceof BpmnError) {
          error = (BpmnError) cause;
          break;
        }
        cause = cause.getCause();
      }

      if (error != null) {
        ErrorPropagation.propagateError(error, activityExecution);
      } else {
        throw new ActivitiException(exc.getMessage(), exc);
      }

    }
  }

}