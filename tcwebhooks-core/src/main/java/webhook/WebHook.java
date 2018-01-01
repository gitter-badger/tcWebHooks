package webhook;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.http.NameValuePair;

import jetbrains.buildServer.serverSide.SFinishedBuild;
import webhook.teamcity.BuildState;
import webhook.teamcity.BuildStateEnum;
import webhook.teamcity.auth.WebHookAuthenticator;
import webhook.teamcity.payload.util.TemplateMatcher.VariableResolver;
import webhook.teamcity.settings.WebHookFilterConfig;

public interface WebHook {

	public abstract void setProxy(WebHookProxyConfig proxyConfig);

	public abstract void setProxy(String proxyHost, Integer proxyPort);

	public abstract void setProxyUserAndPass(String username, String password);

	public abstract void post() throws IOException;

	public abstract Integer getStatus();

	public abstract String getProxyHost();

	public abstract int getProxyPort();

	public abstract String getUrl();

	public abstract void setUrl(String url);

	public abstract String getParameterisedUrl();

	public abstract String parametersAsQueryString();

	public abstract void addParam(String key, String value);

	public abstract void addParams(List<NameValuePair> paramsList);
	
	public abstract void addParams(Map<String, String> paramsList);

	public abstract String getParam(String key);

	public abstract String getContent();

	public abstract Boolean isEnabled();

	public abstract void setEnabled(Boolean enabled);

	public abstract void setEnabled(String enabled);

	public abstract Boolean isErrored();

	public abstract void setErrored(Boolean errored);

	public abstract String getErrorReason();

	public abstract void setErrorReason(String errorReason);

	public abstract BuildState getBuildStates();
	
	public abstract void setBuildStates(BuildState states);

	public abstract String getProxyUsername();

	public abstract void setProxyUsername(String proxyUsername);

	public abstract String getProxyPassword();

	public abstract void setProxyPassword(String proxyPassword);

	public abstract String getPayload();

	public abstract void setPayload(String payloadContent);

	public abstract void setContentType(String contentType);

	public abstract void setCharset(String charset);

	public abstract void setAuthentication(WebHookAuthenticator authenticator);

	/**
	 * Returns true if all enabled filters match.
	 * @param variableResolver
	 * @return boolean indicating if webhook is enabled.
	 */
	public abstract boolean checkFilters(VariableResolver variableResolver);

	public abstract void addFilter(WebHookFilterConfig filterHolder);

	public abstract String getDisabledReason();

	public abstract WebHookExecutionStats getExecutionStats();

	public abstract SFinishedBuild getPreviousNonPersonalBuild();

	public abstract void setPreviousNonPersonalBuild(SFinishedBuild localSFinishedBuild);

	public abstract void setConnectionTimeOut(int httpConnectionTimeout);

	public abstract void setResponseTimeOut(int httpResponseTimeout);

	public abstract void setEnabledForBuildState(BuildStateEnum buildState, boolean enabled);

}