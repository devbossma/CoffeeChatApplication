package dev.saberlabs.coffeechat.e2e;

import com.jayway.jsonpath.JsonPath;
import dev.saberlabs.coffeechat.config.ManagerBootstrap;
import dev.saberlabs.coffeechat.service.StaffService;
import dev.saberlabs.coffeechat.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * Runs the curl walkthrough in {@code docs/API.md} against the real application, so the documentation is
 * verified by CI: every {@code curl} command in that section is parsed out of the file and sent over MockMvc,
 * and the status code and the {@code "key":value} pairs of the sample response written under it must match what
 * the application really answers. If the documentation and the behaviour disagree, this test fails.
 *
 * <p>What is compared: the expected status (a {@code # 204} comment, otherwise any 2xx), and every
 * {@code "key": scalar} in the sample response except {@code "..."} placeholders, matched anywhere in the real
 * response (numbers compared numerically). A {@code GET} is retried until it matches (the barista threads prepare
 * the order asynchronously); anything else must match at once. The database is reset with restarted identity
 * columns first, so the ids the document promises (manager 1, customer 2, barista 3) really are the ones issued.
 */
@DisplayName("docs/API.md walkthrough is executable")
class ApiDocWalkthroughTest extends AbstractIntegrationTest {

    private static final Path DOC = Path.of("docs/API.md");
    private static final Pattern PAIR = Pattern.compile("\"(\\w+)\"\\s*:\\s*(\"(?:[^\"\\\\]|\\\\.)*\"|-?\\d+(?:\\.\\d+)?|true|false)");

    @Autowired MockMvc mvc;
    @Autowired StaffService staff;

    @Override
    protected boolean baristasLive() {
        return true;
    }

    /** One curl command of the document with what the document says it returns. */
    record Step(int line, String method, String path, Map<String, String> headers, String body, Integer status,
                Map<String, List<String>> expected) {
    }

    // ------------------------------------------------------------------ parsing the document

    static List<Step> parseWalkthrough(List<String> lines) {
        int start = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith("## Walkthrough")) {
                start = i;
                break;
            }
        }
        assertTrue(start >= 0, "docs/API.md has no '## Walkthrough' section");
        int open = start;
        while (!lines.get(open).startsWith("```bash")) {
            open++;
        }
        List<Step> steps = new ArrayList<>();
        for (int i = open + 1; i < lines.size() && !lines.get(i).startsWith("```"); i++) {
            String line = lines.get(i).strip();
            if (!line.startsWith("curl ")) {
                continue;
            }
            List<String> tokens = new ArrayList<>();
            StringBuilder comment = new StringBuilder();
            tokenize(line.replace("$B", "").replace("$J", "Content-Type: application/json"), tokens, comment);
            List<String> expectation = new ArrayList<>();
            if (comment.length() > 0) {
                expectation.add(comment.toString().strip());
            }
            int j = i + 1;
            while (j < lines.size() && lines.get(j).strip().startsWith("#")) {
                expectation.add(lines.get(j).strip().substring(1).strip());
                j++;
            }
            steps.add(toStep(i + 1, tokens, expectation));
        }
        return steps;
    }

    /** A minimal shell-word splitter: single and double quotes, and an unquoted # starts a comment. */
    static void tokenize(String line, List<String> tokens, StringBuilder comment) {
        StringBuilder current = new StringBuilder();
        boolean inWord = false;
        char quote = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                } else {
                    current.append(c);
                }
            } else if (c == '\'' || c == '"') {
                quote = c;
                inWord = true;
            } else if (c == '#' && !inWord) {
                comment.append(line.substring(i + 1));
                break;
            } else if (Character.isWhitespace(c)) {
                if (inWord) {
                    tokens.add(current.toString());
                    current.setLength(0);
                    inWord = false;
                }
            } else {
                current.append(c);
                inWord = true;
            }
        }
        if (inWord) {
            tokens.add(current.toString());
        }
    }

    private static Step toStep(int line, List<String> tokens, List<String> expectation) {
        String method = null;
        String path = null;
        String body = null;
        Map<String, String> headers = new LinkedHashMap<>();
        for (int t = 1; t < tokens.size(); t++) {
            String token = tokens.get(t);
            switch (token) {
                case "-s" -> { }
                case "-X" -> method = tokens.get(++t);
                case "-H" -> {
                    String header = tokens.get(++t);
                    int colon = header.indexOf(':');
                    headers.put(header.substring(0, colon).strip(), header.substring(colon + 1).strip());
                }
                case "-d" -> body = tokens.get(++t);
                default -> {
                    if (token.startsWith("/")) {
                        path = token;
                    } else {
                        throw new IllegalStateException("docs/API.md line " + line + ": unsupported curl token " + token);
                    }
                }
            }
        }
        if (path == null) {
            throw new IllegalStateException("docs/API.md line " + line + ": no URL");
        }
        Integer status = null;
        StringBuilder text = new StringBuilder();
        for (String e : expectation) {
            if (status == null && e.matches("\\d{3}")) {
                status = Integer.valueOf(e);
            } else {
                text.append(e).append('\n');
            }
        }
        Map<String, List<String>> expected = new LinkedHashMap<>();
        Matcher m = PAIR.matcher(text);
        while (m.find()) {
            String value = m.group(2);
            if (value.equals("\"...\"")) {
                continue;
            }
            expected.computeIfAbsent(m.group(1), k -> new ArrayList<>()).add(value);
        }
        return new Step(line, method == null ? "GET" : method, path, headers, body, status, expected);
    }

    // ------------------------------------------------------------------ running it

    private MvcResult send(Step step) throws Exception {
        MockHttpServletRequestBuilder builder = request(HttpMethod.valueOf(step.method()), step.path());
        step.headers().forEach(builder::header);
        if (step.body() != null) {
            builder.content(step.body());
        }
        return mvc.perform(builder).andReturn();
    }

    /** Empty when the response agrees with the document. */
    private static List<String> mismatches(Step step, MvcResult result) throws Exception {
        List<String> problems = new ArrayList<>();
        int actual = result.getResponse().getStatus();
        if (step.status() != null ? actual != step.status() : actual / 100 != 2) {
            problems.add("status " + actual + ", the document says " + (step.status() == null ? "2xx" : step.status()));
        }
        String body = result.getResponse().getContentAsString();
        for (Map.Entry<String, List<String>> entry : step.expected().entrySet()) {
            List<Object> found = body.isBlank() ? List.of() : JsonPath.parse(body).read("$.." + entry.getKey());
            for (String documented : entry.getValue()) {
                if (found.stream().noneMatch(v -> sameValue(documented, v))) {
                    problems.add("\"" + entry.getKey() + "\": " + documented + " is documented but the response has " + found);
                }
            }
        }
        return problems;
    }

    private static boolean sameValue(String documented, Object actual) {
        if (actual == null) {
            return false;
        }
        if (documented.startsWith("\"")) {
            return actual instanceof String s && s.equals(documented.substring(1, documented.length() - 1).replace("\\\"", "\""));
        }
        if (documented.equals("true") || documented.equals("false")) {
            return String.valueOf(actual).equals(documented);
        }
        return actual instanceof Number && new BigDecimal(documented).compareTo(new BigDecimal(actual.toString())) == 0;
    }

    @Test
    @DisplayName("the parser reads the walkthrough (guards against this test passing vacuously)")
    void parserFindsTheSteps() throws IOException {
        List<Step> steps = parseWalkthrough(Files.readAllLines(DOC));

        assertTrue(steps.size() >= 10, "expected the ten documented steps, found " + steps.size());
        assertEquals("POST", steps.get(0).method());
        assertEquals("/api/customers", steps.get(0).path());
        assertEquals(204, steps.get(2).status());
        assertTrue(steps.stream().anyMatch(s -> s.body() != null && s.body().contains("/order latte milk")));
        assertTrue(steps.stream().anyMatch(s -> s.expected().containsKey("orderId")));
    }

    @Test
    @DisplayName("every curl step in docs/API.md returns what the document says it returns")
    void walkthroughMatchesTheApplication() throws Exception {
        jdbc.execute("TRUNCATE chat_messages, chat_sessions, order_status_history, payments, order_extras, orders, user_accounts RESTART IDENTITY CASCADE");
        new ManagerBootstrap(users, staff, "Manager").run(new DefaultApplicationArguments());
        assertEquals(1L, users.findAll().get(0).id(), "the seeded manager must be user 1, as the document says");

        List<Step> steps = parseWalkthrough(Files.readAllLines(DOC));
        for (Step step : steps) {
            String label = "docs/API.md line " + step.line() + ": " + step.method() + " " + step.path();
            if (step.method().equals("GET")) {
                await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(100)).untilAsserted(
                        () -> assertEquals(List.of(), mismatches(step, send(step)), label));
            } else {
                assertEquals(List.of(), mismatches(step, send(step)), label);
            }
        }
    }
}
