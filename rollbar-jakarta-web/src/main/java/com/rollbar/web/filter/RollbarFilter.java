package com.rollbar.web.filter;

import static com.rollbar.notifier.config.ConfigBuilder.withAccessToken;

import com.rollbar.notifier.Rollbar;
import com.rollbar.notifier.config.Config;
import com.rollbar.notifier.config.ConfigBuilder;
import com.rollbar.notifier.config.ConfigProvider;
import com.rollbar.notifier.config.ConfigProviderHelper;
import com.rollbar.web.provider.PersonProvider;
import com.rollbar.web.provider.RequestProvider;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RollbarFilter implements Filter {

  private static final Logger LOGGER = LoggerFactory.getLogger(RollbarFilter.class);

  static final String ACCESS_TOKEN_PARAM_NAME = "access_token";

  static final String USER_IP_HEADER_PARAM_NAME = "user_ip_header";

  static final String CONFIG_PROVIDER_CLASS_PARAM_NAME = "config_provider";

  static final String CONFIG_IP_CAPTURE_PARAM_NAME = "capture_ip";

  private Rollbar rollbar;

  public RollbarFilter() {
    // Empty constructor.
  }

  public RollbarFilter(Rollbar rollbar) {
    this.rollbar = rollbar;
  }

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
    String accessToken = filterConfig.getInitParameter(ACCESS_TOKEN_PARAM_NAME);
    String userIpHeaderName = filterConfig.getInitParameter(USER_IP_HEADER_PARAM_NAME);
    String configProviderClassName =
        filterConfig.getInitParameter(CONFIG_PROVIDER_CLASS_PARAM_NAME);
    String captureIp = filterConfig.getInitParameter(CONFIG_IP_CAPTURE_PARAM_NAME);

    ConfigProvider configProvider = ConfigProviderHelper.getConfigProvider(configProviderClassName);
    Config config;

    RequestProvider requestProvider = new RequestProvider.Builder()
        .userIpHeaderName(userIpHeaderName)
        .captureIp(captureIp)
        .build();

    ConfigBuilder configBuilder = withAccessToken(accessToken)
        .request(requestProvider)
        .person(new PersonProvider());

    if (configProvider != null) {
      config = configProvider.provide(configBuilder);
    } else {
      config = configBuilder.build();
    }

    rollbar = Rollbar.init(config);
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    try {
      chain.doFilter(request, response);
    } catch (Exception e) {
      sendToRollbar(e);
      throw e;
    }
  }

  private void sendToRollbar(Exception error) {
    try {
      rollbar.error(error);
    } catch (Exception e) {
      LOGGER.error("Error sending to rollbar the error: ", error, e);
    }
  }

  @Override
  public void destroy() {
    rollbar = null;
  }
}
