package yandex.cloud.toolkit.configuration.function.deploy

import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionException
import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import yandex.cloud.api.serverless.functions.v1.FunctionOuterClass
import yandex.cloud.toolkit.process.FunctionDeployProcess
import yandex.cloud.toolkit.process.RunContentController
import yandex.cloud.toolkit.util.logger

class DeployFunctionConfiguration(name: String?, factory: ConfigurationFactory, project: Project) :
    RunConfigurationMinimalBase<FunctionDeploySpec>(name, factory, project) {

    override fun clone() = DeployFunctionConfiguration(name, factory, project).apply {
        loadState(this@DeployFunctionConfiguration.state)
    }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> =
        DeployFunctionConfigurationEditor(project, null, null, null, null)

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState {
        if (state.functionId.isNullOrEmpty()) {
            throw ExecutionException("No function ID defined")
        }
        return RunProfileState { _, _ ->
            val console = TextConsoleBuilderFactory.getInstance().createBuilder(project).apply {
                setViewer(true)
            }.console

            val processController = RunContentController()
            val process = FunctionDeployProcess(project, state, processController, console.logger)

            console.attachToProcess(processController)
            invokeLater(ModalityState.NON_MODAL, process::start)
            DefaultExecutionResult(console, processController)
        }
    }

    override fun getState(): FunctionDeploySpec = options

    companion object {
        fun createTemplateConfiguration(
            project: Project,
            template: FunctionOuterClass.Version? = null
        ): DeployFunctionConfiguration {
            val configurationType = runConfigurationType<DeployFunctionConfigurationType>()
            val configuration = configurationType.createTemplateConfiguration(project)

            if (template != null) {
                val templateSpec = FunctionDeploySpec.from(template)
                configuration.loadState(templateSpec)
            }

            return configuration
        }
    }
}

class FunctionDeploySpec : BaseState() {
    var functionId by string()
    var runtime by string()
    var entryPoint by string()
    var sourceFiles by list<String>()
    var sourceFolderPolicy by string()
    var description by string()
    var memoryBytes by property(128L * 1024 * 1024)
    var timeoutSeconds by property(3L)
    var serviceAccountId by string()
    var envVariables by map<String, String>()
    var tags by list<String>()

    companion object {

        val DEFAULT_TIMEOUT = 7
        val DEFAULT_MEMORY_BYTES = 128L * 1024 * 1024

        fun template() = FunctionDeploySpec().apply() {
            timeoutSeconds = DEFAULT_TIMEOUT.toLong()
            memoryBytes = DEFAULT_MEMORY_BYTES
        }

        fun from(template: FunctionOuterClass.Version) = FunctionDeploySpec().apply {
            functionId = template.functionId
            timeoutSeconds = template.executionTimeout.seconds
            entryPoint = template.entrypoint
            description = template.description
            runtime = template.runtime
            envVariables = template.environmentMap
            memoryBytes = template.resources.memory
            serviceAccountId = template.serviceAccountId
            tags = template.tagsList
        }
    }
}