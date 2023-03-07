package com.rollbar.web.provider;

import static java.util.Arrays.asList;

import com.rollbar.api.payload.data.Request;
import com.rollbar.notifier.provider.Provider;
import com.rollbar.web.listener.RollbarRequestListener;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * {@link Request} provider.
 */
public class RequestProvider implements Provider<Request> {

  private final String userIpHeaderName;
  private final int captureIp;

  // CAPTURE_IP_ANONYMIZE is the string value used to signify anonymizing captured IP addresses
  public static final String CAPTURE_IP_ANONYMIZE = "anonymize";
  // CAPTURE_IP_NONE is the string value used to signify not capturing IP addresses
  public static final String CAPTURE_IP_NONE = "none";

  private static final int CAPTURE_IP_TYPE_FULL = 0;
  private static final int CAPTURE_IP_TYPE_ANONYMIZE = 1;
  private static final int CAPTURE_IP_TYPE_NONE = 2;

  /**
   * Constructor.
   */
  RequestProvider(Builder builder) {
    this.userIpHeaderName = builder.userIpHeaderName;
    if (builder.captureIp != null) {
      if (builder.captureIp.equals(CAPTURE_IP_ANONYMIZE)) {
        this.captureIp = CAPTURE_IP_TYPE_ANONYMIZE;
      } else if (builder.captureIp.equals(CAPTURE_IP_NONE)) {
        this.captureIp = CAPTURE_IP_TYPE_NONE;
      } else {
        this.captureIp = CAPTURE_IP_TYPE_FULL;
      }
    } else {
      this.captureIp = CAPTURE_IP_TYPE_FULL;
    }
  }

  @Override
  public Request provide() {
    HttpServletRequest req = RollbarRequestListener.getServletRequest();

    if (req != null) {
      Request request = new Request.Builder()
          .url(url(req))
          .method(method(req))
          .headers(headers(req))
          .get(getParams(req))
          .post(postParams(req))
          .queryString(queryString(req))
          .userIp(userIp(req))
          .build();

      return request;
    }

    return null;
  }

  private String userIp(HttpServletRequest request) {
    String rawIp;
    if (userIpHeaderName == null || "".equals(userIpHeaderName)) {
      rawIp = request.getRemoteAddr();
    } else {
      rawIp = request.getHeader(userIpHeaderName);
    }
    if (rawIp == null) {
      return rawIp;
    }

    if (captureIp == CAPTURE_IP_TYPE_FULL) {
      return rawIp;
    } else if (captureIp == CAPTURE_IP_TYPE_ANONYMIZE) {
      if (rawIp.contains(".")) {
        // IPV4
        String[] parts = rawIp.split("\\.");
        if (parts.length < 3) {
          return rawIp;
        }
        // Java 7 does not have String.join
        StringBuffer ip = new StringBuffer(parts[0]);
        ip.append(".");
        ip.append(parts[1]);
        ip.append(".");
        ip.append(parts[2]);
        ip.append(".0");
        return ip.toString();
      } else if (rawIp.contains(":")) {
        // IPV6
        String[] parts = rawIp.split(":");
        if (parts.length < 3) {
          return rawIp;
        }
        StringBuffer ip = new StringBuffer(parts[0]);
        ip.append(":");
        ip.append(parts[1]);
        ip.append(":");
        ip.append(parts[2]);
        ip.append(":0000:0000:0000:0000:0000");
        return ip.toString();
      } else {
        return rawIp;
      }
    } else if (captureIp == CAPTURE_IP_TYPE_NONE) {
      return null;
    }
    return null;
  }

  private static String url(HttpServletRequest request) {
    return request.getRequestURL().toString();
  }

  private static String method(HttpServletRequest request) {
    return request.getMethod();
  }

  private static Map<String, String> headers(HttpServletRequest request) {
    Map<String, String> headers = new HashMap<>();

    Enumeration<String> headerNames = request.getHeaderNames();
    while (headerNames.hasMoreElements()) {
      String headerName = headerNames.nextElement();
      headers.put(headerName, request.getHeader(headerName));
    }

    return headers;
  }

  private static Map<String, List<String>> getParams(HttpServletRequest request) {
    if ("GET".equalsIgnoreCase(request.getMethod())) {
      return params(request);
    }

    return null;
  }

  private static Map<String, Object> postParams(HttpServletRequest request) {
    if ("POST".equalsIgnoreCase(request.getMethod())) {
      Map<String, List<String>> params = params(request);
      Map<String, Object> postParams = new HashMap<>();
      for (Entry<String, List<String>> entry : params.entrySet()) {
        if (entry.getValue() != null && entry.getValue().size() == 1) {
          postParams.put(entry.getKey(), entry.getValue().get(0));
        } else {
          postParams.put(entry.getKey(), entry.getValue());
        }
      }
      return postParams;
    }

    return null;
  }

  private static Map<String, List<String>> params(HttpServletRequest request) {
    Map<String, List<String>> params = new HashMap<>();

    Map<String, String[]> paramNames = request.getParameterMap();
    for (Entry<String, String[]> param : paramNames.entrySet()) {
      if (param.getValue() != null && param.getValue().length > 0) {
        params.put(param.getKey(), asList(param.getValue()));
      }
    }

    return params;
  }

  private static String queryString(HttpServletRequest request) {
    return request.getQueryString();
  }

  /**
   * Builder class for {@link RequestProvider}.
   */
  public static final class Builder {

    private String userIpHeaderName;
    private String captureIp;

    /**
     * The request header name to retrieve the user ip.
     * @param userIpHeaderName the header name.
     * @return the builder instance.
     */
    public Builder userIpHeaderName(String userIpHeaderName) {
      this.userIpHeaderName = userIpHeaderName;
      return this;
    }

    /**
     * The policy to use for capturing the user ip.
     * @param captureIp One of: full, anonymize, none
     *     If this value is empty, null, or otherwise invalid the default policy is full.
     * @return the builder instance.
     */
    public Builder captureIp(String captureIp) {
      this.captureIp = captureIp;
      return this;
    }

    /**
     * Builds the {@link RequestProvider request provider}.
     *
     * @return the payload.
     */
    public RequestProvider build() {
      return new RequestProvider(this);
    }
  }
}
