package lk.bookhive.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

  private final RequestIdFilter filter = new RequestIdFilter();

  @Test
  void generatesIdPutsItInMdcDuringRequestAndClearsAfter() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicReference<String> seenInMdc = new AtomicReference<>();

    filter.doFilter(
        new MockHttpServletRequest(),
        response,
        (req, res) -> seenInMdc.set(MDC.get(RequestIdFilter.MDC_KEY)));

    String header = response.getHeader(RequestIdFilter.HEADER);
    assertThat(header).isNotBlank();
    assertThat(seenInMdc.get()).isEqualTo(header);
    assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
  }

  @Test
  void reusesSafeIncomingId() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(RequestIdFilter.HEADER, "abc-123");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, (req, res) -> {});

    assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo("abc-123");
  }

  @Test
  void replacesUnsafeIncomingId() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(RequestIdFilter.HEADER, "fake\nERROR admin logged in");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, (req, res) -> {});

    assertThat(response.getHeader(RequestIdFilter.HEADER)).doesNotContain("admin");
  }
}
