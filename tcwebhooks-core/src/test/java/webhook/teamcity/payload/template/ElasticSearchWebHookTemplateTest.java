package webhook.teamcity.payload.template;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.serverSide.BuildHistory;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.serverSide.SFinishedBuild;

import org.jdom.JDOMException;
import org.junit.Test;

import webhook.WebHook;
import webhook.teamcity.BuildStateEnum;
import webhook.teamcity.MockSBuildType;
import webhook.teamcity.MockSProject;
import webhook.teamcity.MockSRunningBuild;
import webhook.teamcity.WebHookContentBuilder;
import webhook.teamcity.WebHookFactory;
import webhook.teamcity.WebHookFactoryImpl;
import webhook.teamcity.WebHookHttpClientFactory;
import webhook.teamcity.WebHookHttpClientFactoryImpl;
import webhook.teamcity.auth.WebHookAuthenticatorProvider;
import webhook.teamcity.payload.WebHookPayloadManager;
import webhook.teamcity.payload.WebHookTemplateManager;
import webhook.teamcity.payload.WebHookTemplateResolver;
import webhook.teamcity.payload.content.WebHookPayloadContentAssemblyException;
import webhook.teamcity.payload.format.WebHookPayloadJsonTemplate;
import webhook.teamcity.settings.WebHookConfig;
import webhook.teamcity.settings.WebHookMainSettings;
import webhook.teamcity.settings.entity.WebHookTemplateJaxHelper;
import webhook.teamcity.settings.entity.WebHookTemplateJaxTestHelper;
import webhook.testframework.util.ConfigLoaderUtil;

public class ElasticSearchWebHookTemplateTest {

	private WebHookContentBuilder webHookContentBuilder;
	private WebHookTemplateResolver templateResolver;

	@Test
	public void test() throws JDOMException, IOException, WebHookPayloadContentAssemblyException {
		SBuildServer sBuildServer = mock(SBuildServer.class);
		WebHookMainSettings mainSettings = mock(WebHookMainSettings.class);
		WebHookTemplateJaxHelper webHookTemplateJaxHelper = new WebHookTemplateJaxTestHelper();
		WebHookAuthenticatorProvider authenticatorProvider = new WebHookAuthenticatorProvider();
		WebHookPayloadManager payloadManager = new WebHookPayloadManager(sBuildServer);
		WebHookTemplateManager templateManager = new WebHookTemplateManager(payloadManager, webHookTemplateJaxHelper);
		WebHookHttpClientFactory clientFactory = new WebHookHttpClientFactoryImpl();
		
		WebHookPayloadJsonTemplate webHookPayloadJsonTemplate = new WebHookPayloadJsonTemplate(payloadManager);
		webHookPayloadJsonTemplate.register();

		ElasticSearchXmlWebHookTemplate elasticTemplate = new ElasticSearchXmlWebHookTemplate(templateManager, payloadManager, webHookTemplateJaxHelper);
		templateResolver = new WebHookTemplateResolver(templateManager);
		
		elasticTemplate.register();
		webHookContentBuilder = new WebHookContentBuilder(payloadManager, templateResolver);
		
		WebHookConfig webhookElastic  = ConfigLoaderUtil.getFirstWebHookInConfig(new File("src/test/resources/project-settings-test-elastic.xml"));
		when(mainSettings.getProxyConfigForUrl(webhookElastic.getUrl())).thenReturn(null);
		
		WebHookFactory webHookFactory = new WebHookFactoryImpl(mainSettings, authenticatorProvider, clientFactory);
		WebHook wh = webHookFactory.getWebHook(webhookElastic,null);
		
		MockSBuildType sBuildType = new MockSBuildType("Test Build", "A Test Build", "bt1");
		String triggeredBy = "SubVersion";
		MockSRunningBuild sRunningBuild = new MockSRunningBuild(sBuildType, triggeredBy, Status.NORMAL, "Running", "TestBuild01");
		SFinishedBuild previousBuild = mock(SFinishedBuild.class);
		when (previousBuild.getFinishDate()).thenReturn(new Date());
		List<SFinishedBuild> finishedBuilds = new ArrayList<>();
		finishedBuilds.add(previousBuild);
		BuildHistory buildHistory = mock(BuildHistory.class);
		when(buildHistory.getEntriesBefore(sRunningBuild, false)).thenReturn(finishedBuilds);
		when (sBuildServer.getHistory()).thenReturn(buildHistory);
		MockSProject sProject = new MockSProject("Test Project", "A test project", "project1", "ATestProject", sBuildType);
		sBuildType.setProject(sProject);

		wh = webHookContentBuilder.buildWebHookContent(wh, webhookElastic, sRunningBuild, BuildStateEnum.BUILD_STARTED, true);
		System.out.println(wh.getPayload());
		//wh = webHookContentBuilder.buildWebHookContent(wh, whc, sRunningBuild, state, overrideIsEnabled);
	}

}
