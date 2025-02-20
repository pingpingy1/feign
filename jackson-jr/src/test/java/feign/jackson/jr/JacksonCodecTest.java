/*
 * Copyright 2012-2024 The Feign Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package feign.jackson.jr;

import static feign.Util.UTF_8;
import static feign.assertj.FeignAssertions.assertThat;
import static java.util.Collections.singletonList;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.jr.ob.JSON;
import feign.Request;
import feign.Request.HttpMethod;
import feign.RequestTemplate;
import feign.Response;
import feign.Util;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class JacksonCodecTest {

  private static final String DATES_JSON = "[\"2020-01-02\",\"2021-02-03\"]";

  @Test
  void encodesMapObjectNumericalValuesAsInteger() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("foo", 1);

    RequestTemplate template = new RequestTemplate();
    new JacksonJrEncoder().encode(map, map.getClass(), template);

    assertThat(template).hasBody("{\"foo\":1}");
  }

  @Test
  void encodesMapObjectNumericalValuesToByteArray() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("foo", 1);

    RequestTemplate template = new RequestTemplate();
    new JacksonJrEncoder().encode(map, byte[].class, template);

    assertThat(template).hasBody("{\"foo\":1}");
  }

  @Test
  void encodesFormParams() {
    Map<String, Object> form = new LinkedHashMap<>();
    form.put("foo", 1);
    form.put("bar", Arrays.asList(2, 3));

    RequestTemplate template = new RequestTemplate();
    new JacksonJrEncoder().encode(form, new TypeReference<Map<String, ?>>() {}.getType(), template);

    assertThat(template).hasBody("{\"foo\":1,\"bar\":[2,3]}");
  }

  @Test
  void decodes() throws Exception {
    List<Zone> zones = new LinkedList<>();
    zones.add(new Zone("denominator.io."));
    zones.add(new Zone("denominator.io.", "ABCD"));

    String zonesJson =
        "[{\"name\":\"denominator.io.\"},{\"name\":\"denominator.io.\",\"id\":\"ABCD\"}]";

    Response response = Response.builder()
        .status(200)
        .reason("OK")
        .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
        .headers(Collections.emptyMap())
        .body(zonesJson, UTF_8)
        .build();
    assertThat(
        new JacksonJrDecoder().decode(response, new TypeReference<List<Zone>>() {}.getType()))
            .isEqualTo(zones);
  }

  @Test
  void nullBodyDecodesToEmpty() throws Exception {
    Response response = Response.builder()
        .status(204)
        .reason("OK")
        .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
        .headers(Collections.emptyMap())
        .build();
    assertThat((byte[]) new JacksonJrDecoder().decode(response, byte[].class)).isEmpty();
  }

  @Test
  void emptyBodyDecodesToEmpty() throws Exception {
    Response response = Response.builder()
        .status(204)
        .reason("OK")
        .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
        .headers(Collections.emptyMap())
        .body(new byte[0])
        .build();
    assertThat((byte[]) new JacksonJrDecoder().decode(response, byte[].class)).isEmpty();
  }

  @Test
  void customDecoder() throws Exception {
    JacksonJrDecoder decoder = new JacksonJrDecoder(
        singletonList(new JavaLocalDateExtension()));

    List<LocalDate> dates = new LinkedList<>();
    dates.add(LocalDate.of(2020, 1, 2));
    dates.add(LocalDate.of(2021, 2, 3));

    Response response = Response.builder()
        .status(200)
        .reason("OK")
        .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
        .headers(Collections.emptyMap())
        .body(DATES_JSON, UTF_8)
        .build();
    assertThat(decoder.decode(response, new TypeReference<List<LocalDate>>() {}.getType()))
        .isEqualTo(dates);
  }

  @Test
  void customDecoderExpressedAsMapper() throws Exception {
    JSON mapper = JSON.builder()
        .register(new JavaLocalDateExtension())
        .build();
    JacksonJrDecoder decoder = new JacksonJrDecoder(mapper);

    List<LocalDate> dates = new LinkedList<>();
    dates.add(LocalDate.of(2020, 1, 2));
    dates.add(LocalDate.of(2021, 2, 3));

    Response response = Response.builder()
        .status(200)
        .reason("OK")
        .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
        .headers(Collections.emptyMap())
        .body(DATES_JSON, UTF_8)
        .build();
    assertThat(decoder.decode(response, new TypeReference<List<LocalDate>>() {}.getType()))
        .isEqualTo(dates);
  }

  @Test
  void customEncoder() {
    JacksonJrEncoder encoder = new JacksonJrEncoder(
        singletonList(new JavaLocalDateExtension()));

    List<LocalDate> dates = new LinkedList<>();
    dates.add(LocalDate.of(2020, 1, 2));
    dates.add(LocalDate.of(2021, 2, 3));

    RequestTemplate template = new RequestTemplate();
    encoder.encode(dates, new TypeReference<List<LocalDate>>() {}.getType(), template);

    assertThat(template).hasBody(DATES_JSON);
  }

  @Test
  void decoderCharset() throws IOException {
    Zone zone = new Zone("denominator.io.", "ÁÉÍÓÚÀÈÌÒÙÄËÏÖÜÑ");

    Map<String, Collection<String>> headers = new HashMap<>();
    headers.put("Content-Type", Arrays.asList("application/json;charset=ISO-8859-1"));

    Response response = Response.builder()
        .status(200)
        .reason("OK")
        .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
        .headers(headers)
        .body(new String("" //
            + "{" + System.lineSeparator()
            + "  \"name\" : \"DENOMINATOR.IO.\"," + System.lineSeparator()
            + "  \"id\" : \"ÁÉÍÓÚÀÈÌÒÙÄËÏÖÜÑ\"" + System.lineSeparator()
            + "}").getBytes(StandardCharsets.ISO_8859_1))
        .build();
    assertThat(((Zone) new JacksonJrDecoder().decode(response, Zone.class))
        .getId()).isEqualTo(zone.getId());
  }

  @Test
  void decodesToMap() throws Exception {
    String json = "{\"name\":\"jim\",\"id\":12}";

    Response response = Response.builder()
        .status(200)
        .reason("OK")
        .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
        .headers(Collections.emptyMap())
        .body(json, UTF_8)
        .build();

    Map<String, Object> map = (Map<String, Object>) new JacksonJrDecoder()
        .decode(response, new TypeReference<Map<String, Object>>() {}.getType());

    assertThat(map).containsEntry("id", 12);
    assertThat(map).containsEntry("name", "jim");
  }

  public static class Zone {

    private String name;
    private String id;

    public Zone() {
      // for reflective instantiation.
    }

    public Zone(String name) {
      this.name = name;
    }

    public Zone(String name, String id) {
      this.name = name;
      this.id = id;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      Zone zone = (Zone) o;
      return Objects.equals(name, zone.name) && Objects.equals(id, zone.id);
    }

    @Override
    public int hashCode() {
      return Objects.hash(name, id);
    }

    @Override
    public String toString() {
      return "Zone{" +
          "name='" + name + '\'' +
          ", id='" + id + '\'' +
          '}';
    }
  }

  /** Enabled via {@link feign.Feign.Builder#dismiss404()} */
  @Test
  void notFoundDecodesToEmpty() throws Exception {
    Response response = Response.builder()
        .status(404)
        .reason("NOT FOUND")
        .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
        .headers(Collections.emptyMap())
        .build();
    assertThat((byte[]) new JacksonJrDecoder().decode(response, byte[].class)).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("decodeGenericsArguments")
  void decodeGenerics(Response response, Type responseType, DataWrapper<?> expectedDataWrapper) throws IOException {
    assertThat(new JacksonJrDecoder().decode(response, responseType)).isEqualTo(expectedDataWrapper);
  }

  static class DataWrapper<T> {
    private T data;

    DataWrapper() {
    }

    DataWrapper(T data) {
      this.data = data;
    }

    public void setData(T data) {
      this.data = data;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      DataWrapper<?> that = (DataWrapper<?>) o;
      return Objects.equals(data, that.data);
    }
  }

  static Stream<Arguments> decodeGenericsArguments() {
    Response.Builder responseBuilder = Response
        .builder()
        .request(Request.create(
            HttpMethod.GET,
            "/v1/dummy",
            Collections.emptyMap(),
            Request.Body.empty(),
            null
        ));
    return Stream.of(
        Arguments.of(
            responseBuilder.body("{\"data\":2024}", StandardCharsets.UTF_8).build(),
            new TypeReference<DataWrapper<Integer>>() {}.getType(),
            new DataWrapper<>(2024)
        ),
        Arguments.of(
            responseBuilder.body("{\"data\":\"Hello, World!\"}", StandardCharsets.UTF_8).build(),
            new TypeReference<DataWrapper<String>>() {}.getType(),
            new DataWrapper<>("Hello, World!")
        )
    );
  }
}
