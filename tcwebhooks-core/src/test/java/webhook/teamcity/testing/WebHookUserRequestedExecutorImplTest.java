package webhook.teamcity.testing;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.HashMap;
import java.util.TreeMap;

import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.JAXBException;

import org.jdom.JDOMException;
import org.joda.time.LocalDateTime;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import jetbrains.buildServer.BuildTypeStatusDescriptor;
import jetbrains.buildServer.StatusDescriptor;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.parameters.ParametersProvider;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SBuildAgent;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.SRunningBuild;
import jetbrains.buildServer.serverSide.settings.ProjectSettingsManager;
import webhook.WebHookExecutionStats;
import webhook.WebHookTestServer;
import webhook.WebHookTestServerTestBase;
import webhook.teamcity.BuildState;
import webhook.teamcity.BuildStateEnum;
import webhook.teamcity.MockSBuildAgent;
import webhook.teamcity.MockSBuildType;
import webhook.teamcity.MockSProject;
import webhook.teamcity.MockSRunningBuild;
import webhook.teamcity.TestingWebHookHttpClientFactoryImpl;
import webhook.teamcity.TestingWebHookHttpClientFactoryImpl.TestableHttpClient;
import webhook.teamcity.WebHookContentBuilder;
import webhook.teamcity.WebHookFactory;
import webhook.teamcity.WebHookFactoryImpl;
import webhook.teamcity.WebHookHttpClientFactory;
import webhook.teamcity.WebHookHttpClientFactoryImpl;
import webhook.teamcity.auth.WebHookAuthenticatorProvider;
import webhook.teamcity.history.GeneralisedWebAddress;
import webhook.teamcity.history.GeneralisedWebAddressType;
import webhook.teamcity.history.WebAddressTransformer;
import webhook.teamcity.history.WebAddressTransformerImpl;
import webhook.teamcity.history.WebHookHistoryItem;
import webhook.teamcity.history.WebHookHistoryItem.WebHookErrorStatus;
import webhook.teamcity.history.WebHookHistoryItemFactory;
import webhook.teamcity.payload.WebHookPayload;
import webhook.teamcity.payload.WebHookPayloadManager;
import webhook.teamcity.payload.WebHookTemplateManager;
import webhook.teamcity.payload.WebHookTemplateResolver;
import webhook.teamcity.payload.content.ExtraParametersMap;
import webhook.teamcity.payload.format.WebHookPayloadJsonTemplate;
import webhook.teamcity.settings.WebHookConfig;
import webhook.teamcity.settings.WebHookMainSettings;
import webhook.teamcity.settings.WebHookProjectSettings;
import webhook.teamcity.settings.config.WebHookTemplateConfig.WebHookTemplateBranchText;
import webhook.teamcity.settings.config.WebHookTemplateConfig.WebHookTemplateText;
import webhook.teamcity.settings.entity.WebHookTemplateEntity;
import webhook.teamcity.settings.entity.WebHookTemplateJaxTestHelper;
import webhook.teamcity.testing.model.WebHookExecutionRequest;
import webhook.teamcity.testing.model.WebHookTemplateExecutionRequest;
import webhook.testframework.WebHookMockingFramework;
import webhook.testframework.WebHookMockingFrameworkImpl;

public class WebHookUserRequestedExecutorImplTest extends WebHookTestServerTestBase {
	
	
	private SBuildServer server = mock(SBuildServer.class);
	private ProjectSettingsManager projectSettingsManager = mock(ProjectSettingsManager.class);
	
	private WebHookPayloadManager webHookPayloadManager = new WebHookPayloadManager(server);
	private WebHookConfigFactory webHookConfigFactory = new WebHookConfigFactoryImpl(server, projectSettingsManager, webHookPayloadManager);
	private WebHookTemplateJaxTestHelper webHookTemplateJaxTestHelper = new WebHookTemplateJaxTestHelper();
	private WebHookTemplateManager webHookTemplateManager  = new WebHookTemplateManager(webHookPayloadManager, webHookTemplateJaxTestHelper);
	private WebHookTemplateResolver webHookTemplateResolver = new WebHookTemplateResolver(webHookTemplateManager);
	
	private WebHookPayload jsonTemplate = new WebHookPayloadJsonTemplate(webHookPayloadManager);

	private WebHookMainSettings mainSettings = new WebHookMainSettings(server);
	private WebHookProjectSettings webHookProjectSettings;
	
	private TestableHttpClient httpClient = new TestableHttpClient();
	private WebHookHttpClientFactory webHookHttpClientFactory = new TestingWebHookHttpClientFactoryImpl(httpClient);
	
	private WebHookAuthenticatorProvider webHookAuthenticatorProvider = new WebHookAuthenticatorProvider();

	private WebHookFactory webHookFactory = new WebHookFactoryImpl(mainSettings, webHookAuthenticatorProvider, webHookHttpClientFactory);
	
	
	private MockSBuildType buildType = new MockSBuildType("name", "description", "buildTypeId");
	private SProject sproject = new MockSProject("name", "description", "projectId", "projectExternalId", buildType);
	private SRunningBuild runningBuild = new MockSRunningBuild(buildType, "triggeredBy", Status.NORMAL, "statusText", "buildNumber"); 
	
	private WebHookHistoryItemFactory webHookHistoryItemFactory;
	
	@Mock
	private ParametersProvider parametersProvider;
	
	private final StatusDescriptor runningBuildStatusDescriptor = new StatusDescriptor(Status.NORMAL, "text");
	
	private final Date buildStartDate = new Date();
	
	private final WebAddressTransformer webAddressTransformer = new WebAddressTransformerImpl();
	
	@Mock
	private ProjectManager projectManager;
	
	WebHookMockingFramework framework;
	
	@Before
	public void setup() throws JAXBException, IOException, JDOMException {
		MockitoAnnotations.initMocks(this);
		jsonTemplate.register();
		buildType.setProject(sproject);
		when(server.findBuildInstanceById(1)).thenReturn(runningBuild);
		when(server.findBuildInstanceById(2)).thenReturn(runningBuild);
		when(server.getProjectManager()).thenReturn(projectManager);
		when(projectManager.findProjectById(anyString())).thenReturn(sproject);
		
		framework = WebHookMockingFrameworkImpl.create(BuildStateEnum.BUILD_FINISHED, new ExtraParametersMap(new HashMap<String,String>()), new ExtraParametersMap(new HashMap<String,String>()));
		framework.loadWebHookProjectSettingsFromConfigXml(new File("src/test/resources/project-settings-test-slackcompact-jsonTemplate-AllEnabled.xml"));
		webHookProjectSettings = framework.getWebHookProjectSettings(); 

		when(projectSettingsManager.getSettings(anyString(), anyString())).thenReturn(webHookProjectSettings);
		when(parametersProvider.getAll()).thenReturn(new TreeMap<String,String>());
		
		WebHookTemplateEntity templateEntity = webHookTemplateJaxTestHelper.readTemplate("src/main/resources/webhook/teamcity/payload/template/SlackComCompactWebHookTemplate.xml");
		webHookTemplateManager.registerTemplateFormatFromXmlEntity(templateEntity);
		webHookHistoryItemFactory = new MockWebHookHistoryItemFactory(sproject);
	}

	@Test
	public void testRequestWebHookExecutionWebHookExecutionRequest() {
		WebHookUserRequestedExecutorImpl executorImpl = new WebHookUserRequestedExecutorImpl(
				server, mainSettings,
				projectSettingsManager, 
				webHookConfigFactory, 
				webHookFactory,
				webHookTemplateResolver, 
				webHookPayloadManager, 
				webHookHistoryItemFactory,
				webAddressTransformer
			);
		
		BuildState finishedBuildState = new BuildState();
		finishedBuildState.setEnabled(BuildStateEnum.BUILD_SUCCESSFUL, true);
		
		WebHookExecutionRequest webHookExecutionRequest = WebHookExecutionRequest.builder()
				.buildId(1L)
				.projectId("project01")
				.testBuildState(BuildStateEnum.BUILD_SUCCESSFUL)
				
				.url("http://localhost:12345/webhook")
				.templateId("slack.com-compact")
				.payloadFormat("jsonTemplate")
				.authEnabled(false)
				.configbuildState(finishedBuildState)
				.build();
		WebHookHistoryItem historyItem = executorImpl.requestWebHookExecution(webHookExecutionRequest);
		
		assertEquals("HttpClient should be invoked exactly once", 1, httpClient.getIncovationCount());
		assertEquals("Expect 801 since there is no server running on port 12345", 801, historyItem.getWebhookErrorStatus().getErrorCode());
		assertEquals(true, historyItem.getWebhookErrorStatus().getMessage().contains("Connection refused"));
	}
	
	@Test
	public void testRequestWebHookExecutionWebHookExecutionRequestReturns200() throws InterruptedException {
		WebHookUserRequestedExecutorImpl executorImpl = new WebHookUserRequestedExecutorImpl(
				server, mainSettings,
				projectSettingsManager, 
				webHookConfigFactory, 
				webHookFactory,
				webHookTemplateResolver, 
				webHookPayloadManager, 
				webHookHistoryItemFactory,
				webAddressTransformer
				);
		
		BuildState finishedBuildState = new BuildState();
		finishedBuildState.setEnabled(BuildStateEnum.BUILD_SUCCESSFUL, true);
		
		WebHookExecutionRequest webHookExecutionRequest = WebHookExecutionRequest.builder()
				.buildId(1L)
				.projectId("project01")
				.testBuildState(BuildStateEnum.BUILD_SUCCESSFUL)
				
				.url("http://localhost:58001/200")
				.templateId("slack.com-compact")
				.payloadFormat("jsonTemplate")
				.authEnabled(false)
				.configbuildState(finishedBuildState)
				.build();
		
		WebHookTestServer s = startWebServer();
		
		WebHookHistoryItem historyItem = executorImpl.requestWebHookExecution(webHookExecutionRequest);

		assertEquals("Post should have returned 200 OK", HttpServletResponse.SC_OK, s.getReponseCode());
		assertEquals("HttpClient should be invoked exactly once", 1, httpClient.getIncovationCount());
		assertEquals(false, historyItem.getWebHookExecutionStats().isErrored());
		
		stopWebServer(s);
	}

	@Test
	public void testRequestWebHookExecutionWebHookTemplateExecutionRequest() {
		WebHookUserRequestedExecutorImpl executorImpl = new WebHookUserRequestedExecutorImpl(
				server, mainSettings,
				projectSettingsManager,
				webHookConfigFactory, 
				webHookFactory,
				webHookTemplateResolver, 
				webHookPayloadManager, 
				webHookHistoryItemFactory,
				webAddressTransformer
			);
		
		BuildState finishedBuildState = new BuildState();
		finishedBuildState.setEnabled(BuildStateEnum.BUILD_SUCCESSFUL, true);
		WebHookConfig loadedConfig = webHookProjectSettings.getWebHooksConfigs().get(0);
		
		WebHookTemplateExecutionRequest webHookTemplateExecutionRequest = WebHookTemplateExecutionRequest.builder()
				.buildId(2L)
				.projectId(sproject.getProjectId())
				.testBuildState(BuildStateEnum.BUILD_SUCCESSFUL)
				.uniqueKey(loadedConfig.getUniqueKey())
				.url("http://localhost:12345/webhook")
				.defaultBranchTemplate(new WebHookTemplateBranchText("branch Text for build: ${buildId}"))
				.defaultTemplate(new WebHookTemplateText(false, "non-Branch text for build: ${buildId}"))
				.build();
		WebHookHistoryItem historyItem = executorImpl.requestWebHookExecution(webHookTemplateExecutionRequest);
		
		assertEquals("HttpClient should be invoked exactly once", 1, httpClient.getIncovationCount());
		assertEquals("Expect 801 since there is no server running on port 12345", 801, historyItem.getWebhookErrorStatus().getErrorCode());
		Loggers.SERVER.debug("################# " + historyItem.getWebhookErrorStatus().getMessage());
		assertEquals(true, historyItem.getWebhookErrorStatus().getMessage().contains("Connection refused"));

	}
	
	@Test
	public void testRequestWebHookExecutionWebHookTemplateExecutionRequestReturns200() throws InterruptedException {
		WebHookUserRequestedExecutorImpl executorImpl = new WebHookUserRequestedExecutorImpl(
				server, mainSettings,
				projectSettingsManager,
				webHookConfigFactory, 
				webHookFactory,
				webHookTemplateResolver, 
				webHookPayloadManager, 
				webHookHistoryItemFactory,
				webAddressTransformer
				);
		
		BuildState finishedBuildState = new BuildState();
		finishedBuildState.setEnabled(BuildStateEnum.BUILD_SUCCESSFUL, true);
		WebHookConfig loadedConfig = webHookProjectSettings.getWebHooksConfigs().get(0);
		
		WebHookTemplateExecutionRequest webHookTemplateExecutionRequest = WebHookTemplateExecutionRequest.builder()
				.buildId(2L)
				.projectId(sproject.getProjectId())
				.testBuildState(BuildStateEnum.BUILD_SUCCESSFUL)
				.uniqueKey(loadedConfig.getUniqueKey())
				.url("http://localhost:58001/200")
				.defaultBranchTemplate(new WebHookTemplateBranchText("branch Text for build: ${buildId}"))
				.defaultTemplate(new WebHookTemplateText(false, "non-Branch text for build: ${buildId}"))
				.build();

		WebHookTestServer s = startWebServer();
		
		WebHookHistoryItem historyItem = executorImpl.requestWebHookExecution(webHookTemplateExecutionRequest);

		assertEquals("Post should have returned 200 OK", HttpServletResponse.SC_OK, s.getReponseCode());
		assertEquals("HttpClient should be invoked exactly once", 1, httpClient.getIncovationCount());
		assertEquals(false, historyItem.getWebHookExecutionStats().isErrored());
		
		stopWebServer(s);
		
	}
	
	@Test
	public void testRequestWebHookExecutionWebHookTemplateExecutionRequestWithInvalidWebHook() {
		WebHookUserRequestedExecutorImpl executorImpl = new WebHookUserRequestedExecutorImpl(
				server, mainSettings,
				projectSettingsManager,
				webHookConfigFactory, 
				webHookFactory,
				webHookTemplateResolver, 
				webHookPayloadManager, 
				webHookHistoryItemFactory,
				webAddressTransformer
				);
		
		BuildState finishedBuildState = new BuildState();
		finishedBuildState.setEnabled(BuildStateEnum.BUILD_SUCCESSFUL, true);
		
		WebHookTemplateExecutionRequest webHookTemplateExecutionRequest = WebHookTemplateExecutionRequest.builder()
				.buildId(2L)
				.projectId(sproject.getProjectId())
				.testBuildState(BuildStateEnum.BUILD_SUCCESSFUL)
				.uniqueKey("12345")
				.defaultBranchTemplate(new WebHookTemplateBranchText("branch Text for build: ${buildId}"))
				.defaultTemplate(new WebHookTemplateText(false, "non-Branch text for build: ${buildId}"))
				.build();
		
		WebHookHistoryItem historyItem = executorImpl.requestWebHookExecution(webHookTemplateExecutionRequest);
		
		assertEquals(905, historyItem.getWebhookErrorStatus().getErrorCode());
	}
	
	@Override
	public String getHost() {
		return "localhost";
	}

	@Override
	public Integer getPort() {
		return 58001;
	}
	
	public static class MockWebHookHistoryItemFactory implements WebHookHistoryItemFactory {
		
		private final SProject sProject;
		private WebAddressTransformer webAddressTransformer = new WebAddressTransformerImpl();

		
		public MockWebHookHistoryItemFactory(
				SProject sproject) {
			this.sProject = sproject;
		}

		@Override
		public WebHookHistoryItem getWebHookHistoryItem(WebHookConfig whc, WebHookExecutionStats webHookExecutionStats,
				SBuild sBuild, WebHookErrorStatus errorStatus) {
			try {
				return new WebHookHistoryItem(
						"prodjectId", sProject, "buildTypeId", 
						"buildTypeName", "buildTypeExternalId", 1L, whc, 
						webHookExecutionStats, 
						errorStatus, new LocalDateTime(), 
						webAddressTransformer.getGeneralisedHostName(new URL(whc.getUrl())));
			} catch (MalformedURLException e) {
				e.printStackTrace();
				return null;
			}
		}

		@Override
		public WebHookHistoryItem getWebHookHistoryItem(WebHookConfig whc, WebHookExecutionStats executionStats,
				SBuildType sBuildType, WebHookErrorStatus errorStatus) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public WebHookHistoryItem getWebHookHistoryItem(WebHookConfig whc, WebHookExecutionStats executionStats,
				SProject project, WebHookErrorStatus errorStatus) {
			// TODO Auto-generated method stub
			return null;
		}
		
	}
}