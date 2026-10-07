package com.github.guoyyit.phylogenetictreejava;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"phylo.default-concurrency=2",
                "phylo.work-dir=${java.io.tmpdir}/phylo-it-work"})
class PhyloControllerIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    private final ObjectMapper mapper = new ObjectMapper();

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void fullLifecycleOverHttp() throws Exception {
        String fasta = ">a\\nACGTACGTAAACGTACGTAA\\n"
                + ">b\\nACGTACGTAAACGTACGTAG\\n"
                + ">c\\nACGTTCGTAAATGGTACGTT\\n"
                + ">d\\nTCGTTCGGAATTGGTACGTT\\n";
        String body = "{\"fasta\":\"" + fasta + "\","
                + "\"replicates\":20,\"seed\":7,\"concurrency\":2}";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> created = rest.postForEntity(url("/api/analyses"),
                new HttpEntity<>(body, headers), String.class);
        assertEquals(HttpStatus.ACCEPTED, created.getStatusCode());
        JsonNode createdJson = mapper.readTree(created.getBody());
        String id = createdJson.get("id").asText();

        String status;
        for (int i = 0; i < 100; i++) {
            status = rest.getForObject(url("/api/analyses/" + id), String.class);
            JsonNode node = mapper.readTree(status);
            if ("COMPLETED".equals(node.get("status").asText())) {
                break;
            }
            Thread.sleep(50);
        }

        String consensus = rest.getForObject(
                url("/api/analyses/" + id + "/output/consensus.nwk"), String.class);
        String bootstrapTrees = rest.getForObject(
                url("/api/analyses/" + id + "/output/bootstrap-trees.nwk"), String.class);
        assertTrue(consensus.contains("[1]"));
        assertEquals(20, bootstrapTrees.lines().filter(line -> !line.isBlank()).count());
    }

    @Test
    void invalidInputReturnsDistinguishableReason() throws Exception {
        String body = """
                {"fasta":">a\\nACG\\n>b\\nACGT\\n"}""";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = rest.postForEntity(url("/api/analyses"),
                new HttpEntity<>(body, headers), String.class);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        JsonNode json = mapper.readTree(response.getBody());
        assertEquals("LENGTH_MISMATCH", json.get("error").asText());
    }

    @Test
    void duplicateNamesAreRejectedSynchronouslyWithDistinctReason() throws Exception {
        String fasta = ">a\\nACGT\\n>b\\nACGA\\n>a\\nACGC\\n";
        String body = "{\"fasta\":\"" + fasta + "\"}";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = rest.postForEntity(url("/api/analyses"),
                new HttpEntity<>(body, headers), String.class);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        JsonNode json = mapper.readTree(response.getBody());
        assertEquals("DUPLICATE_NAME", json.get("error").asText());
        assertTrue(json.get("message").asText().contains("a"));
    }

    @Test
    void nonComparablePairIsRejectedSynchronouslyWithInsufficientInformation() throws Exception {
        // Valid overall alignment, but a and b never share a comparable site.
        String fasta = ">a\\nACGTACGT\\n>b\\n--NNNNNN\\n>c\\nTTTTAAAA\\n";
        String body = "{\"fasta\":\"" + fasta + "\"}";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = rest.postForEntity(url("/api/analyses"),
                new HttpEntity<>(body, headers), String.class);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        JsonNode json = mapper.readTree(response.getBody());
        assertEquals("INSUFFICIENT_INFORMATION", json.get("error").asText());
    }
}
