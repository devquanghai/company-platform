package com.company.platform.core.json;

import com.company.platform.core.exception.PlatformInfrastructureException;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonMapperHelperTest {

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final JsonMapperHelper helper = new JsonMapperHelper(mapper);

    @Test
    void convertsJsonObjectsCollectionsMapsTreesBytesAndUpdates() {
        Person person = helper.fromJson("{\"name\":\"Ada\",\"age\":36}", Person.class);
        assertThat(helper.getJsonMapper()).isSameAs(mapper);
        assertThat(person.getName()).isEqualTo("Ada");
        assertThat(helper.toJson(person)).contains("\"name\":\"Ada\"");
        assertThat(helper.fromBytes(helper.toBytes(person), Person.class).getAge()).isEqualTo(36);
        assertThat(helper.fromBytes(
            "[{\"name\":\"Ada\"}]".getBytes(StandardCharsets.UTF_8),
            new TypeReference<List<Person>>() { }
        )).singleElement().extracting(Person::getName).isEqualTo("Ada");
        assertThat(helper.readTree("{\"name\":\"Ada\"}").get("name").asString()).isEqualTo("Ada");
        assertThat(helper.prettyPrint("{\"name\":\"Ada\"}")).contains("\n");

        List<Person> typed = helper.fromJson(
            "[{\"name\":\"Ada\",\"age\":36}]",
            new TypeReference<List<Person>>() { }
        );
        assertThat(typed).singleElement().extracting(Person::getName).isEqualTo("Ada");
        assertThat(helper.fromJsonToList("[{\"name\":\"Ada\"}]", Person.class)).hasSize(1);
        assertThat(helper.fromJsonToMap("{\"one\":1}", String.class, Integer.class))
            .containsEntry("one", 1);

        Map<String, Object> fromObject = helper.toMap(person);
        assertThat(fromObject).containsEntry("name", "Ada");
        assertThat(helper.toMap("{\"name\":\"Ada\"}")).containsEntry("name", "Ada");
        assertThat(helper.fromMap(fromObject, Person.class).getName()).isEqualTo("Ada");
        assertThat(helper.convert(fromObject, Person.class).getAge()).isEqualTo(36);
        assertThat(helper.<Map<String, Object>>convert(
            person,
            new TypeReference<Map<String, Object>>() { }
        )).containsEntry("age", 36);

        Person updated = helper.merge("{\"age\":37}", person);
        assertThat(updated).isSameAs(person);
        assertThat(updated.getAge()).isEqualTo(37);
        assertThat(new String(helper.toBytes(person), StandardCharsets.UTF_8)).contains("37");
    }

    @Test
    void wrapsJsonFailuresWithoutLoggingOrEmbeddingPayloads() {
        assertFailure(() -> helper.fromJson("{", Person.class), "CORE.JSON.DESERIALIZE");
        assertFailure(() -> helper.fromJson("{", new TypeReference<Person>() { }), "CORE.JSON.DESERIALIZE");
        assertFailure(() -> helper.fromBytes(new byte[]{123}, Person.class), "CORE.JSON.DESERIALIZE");
        assertFailure(() -> helper.fromBytes(
            new byte[]{123}, new TypeReference<Person>() { }), "CORE.JSON.DESERIALIZE");
        assertFailure(() -> helper.readTree("{"), "CORE.JSON.DESERIALIZE");
        assertFailure(() -> helper.prettyPrint("{"), "CORE.JSON.DESERIALIZE");
        assertFailure(() -> helper.fromJsonToMap("{", String.class, Integer.class), "CORE.JSON.DESERIALIZE");
        assertFailure(() -> helper.fromJsonToList("{", Person.class), "CORE.JSON.DESERIALIZE");
        assertFailure(() -> helper.convert(Map.of("age", "invalid"), Person.class), "CORE.JSON.CONVERT");
        assertFailure(() -> helper.convert(
            Map.of("age", "invalid"), new TypeReference<Person>() { }), "CORE.JSON.CONVERT");
        assertFailure(() -> helper.merge("{", new Person()), "CORE.JSON.MERGE");
        assertFailure(() -> helper.toJson(new SelfReference()), "CORE.JSON.SERIALIZE");
        assertFailure(() -> helper.toBytes(new SelfReference()), "CORE.JSON.SERIALIZE");
    }

    @Test
    void rejectsNullContracts() {
        assertThatNullPointerException().isThrownBy(() -> new JsonMapperHelper(null));
        assertThatNullPointerException().isThrownBy(() -> helper.fromJson(null, Person.class));
        assertThatNullPointerException().isThrownBy(() -> helper.fromJson("{}", (Class<Person>) null));
        assertThatNullPointerException().isThrownBy(() -> helper.fromJson("{}", (TypeReference<Person>) null));
        assertThatNullPointerException().isThrownBy(() -> helper.fromBytes(null, Person.class));
        assertThatNullPointerException().isThrownBy(() -> helper.fromBytes(
            new byte[]{}, (TypeReference<Person>) null));
        assertThatNullPointerException().isThrownBy(() -> helper.readTree(null));
        assertThatNullPointerException().isThrownBy(() -> helper.toMap(null));
        assertThatNullPointerException().isThrownBy(() -> helper.fromJsonToMap("{}", null, String.class));
        assertThatNullPointerException().isThrownBy(() -> helper.fromJsonToMap("{}", String.class, null));
        assertThatNullPointerException().isThrownBy(() -> helper.fromJsonToList("[]", null));
        assertThatNullPointerException().isThrownBy(() -> helper.fromMap(null, Person.class));
        assertThatNullPointerException().isThrownBy(() -> helper.convert(null, Person.class));
        assertThatNullPointerException().isThrownBy(() -> helper.convert(new Person(), (Class<Person>) null));
        assertThatNullPointerException().isThrownBy(() -> helper.convert(
            new Person(), (TypeReference<Person>) null));
        assertThatNullPointerException().isThrownBy(() -> helper.merge(null, new Person()));
        assertThatNullPointerException().isThrownBy(() -> helper.merge("{}", null));
    }

    private static void assertFailure(Runnable operation, String code) {
        assertThatThrownBy(operation::run)
            .isInstanceOf(PlatformInfrastructureException.class)
            .satisfies(error -> assertThat(((PlatformInfrastructureException) error).errorCode())
                .isEqualTo(code));
    }

    public static final class Person {
        private String name;
        private int age;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getAge() { return age; }
        public void setAge(int age) { this.age = age; }
    }

    public static final class SelfReference {
        public SelfReference getSelf() { return this; }
    }
}
