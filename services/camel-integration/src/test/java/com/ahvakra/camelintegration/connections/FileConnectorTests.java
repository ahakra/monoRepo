package com.ahvakra.camelintegration.connections;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ahvakra.camelintegration.connections.api.*;
import com.ahvakra.camelintegration.connections.domain.*;
import jakarta.persistence.EntityManager;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@Transactional
class FileConnectorTests {

    @Autowired
    ConnectionController controller;

    @Autowired
    ConnectionStore store;

    @Autowired
    EntityManager entities;

    @Autowired
    JsonMapper mapper;

    @TempDir
    Path directory;

    @Test
    void persistsTestsEditsAndDeletesFileConnectorsWithoutPostgresSettings() throws Exception {
        var input = new ConnectionRequest(
            "Inbox",
            "file",
            Map.of(
                "directoryName",
                directory.toString(),
                "move",
                "processed",
                "moveFailed",
                "invalid",
                "includeExt",
                "csv"
            ),
            ""
        );
        var mvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new ConnectionExceptionHandler())
            .build();
        String body = mapper.writeValueAsString(input);
        mvc.perform(post("/api/connections").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty());
        entities.flush();
        entities.clear();
        var saved = store.findAll().getFirst();
        assertEquals("file", saved.type());
        assertNotNull(saved.createdAt());
        assertFalse(saved.settings().containsKey("host"));
        mvc.perform(get("/api/connections"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].settings.directoryName").value(directory.toString()))
            .andExpect(jsonPath("$[0].settings.moveFailed").value("invalid"))
            .andExpect(jsonPath("$[0].hasPassword").value(false));
        mvc.perform(
            post("/api/connections/" + saved.id() + "/test")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
        mvc.perform(
            put("/api/connections/" + saved.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.replace("processed", "archive"))
        ).andExpect(status().isNoContent());
        entities.flush();
        entities.clear();
        var edited = store.findById(saved.id()).orElseThrow();
        assertEquals("archive", edited.settings().get("move"));
        assertEquals(saved.createdAt(), edited.createdAt());
        mvc.perform(
            put("/api/connections/" + saved.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.replace("\"file\"", "\"postgres\""))
        ).andExpect(status().isBadRequest());
        mvc.perform(
            post("/api/connections")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.replace("directoryName", "dir"))
        )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").isNotEmpty());
        mvc.perform(delete("/api/connections/" + saved.id())).andExpect(status().isNoContent());
        assertTrue(store.findById(saved.id()).isEmpty());
    }
}
