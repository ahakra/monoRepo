package com.ahvakra.camelintegration.connections.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ahvakra.camelintegration.connections.domain.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConnectionServiceTests {

    @Test
    void dispatchesNonDatabaseTypesWithoutPostgresFields() {
        var store = mock(ConnectionStore.class);
        var http = mock(ConnectionTypeHandler.class);
        when(http.type()).thenReturn("http");
        var settings = Map.of("url", "https://example.com");
        when(http.validateSettings(settings)).thenReturn(settings);
        when(http.test(any())).thenReturn(new ConnectionTestResult(true, "OK"));
        var service = new ConnectionService(store, List.of(http));
        assertTrue(
            service.test(null, new ConnectionCommand("API", "http", settings, "")).success()
        );
        verify(http).test(
            argThat(
                connection ->
                    connection.type().equals("http") && connection.settings().equals(settings)
            )
        );
        verifyNoInteractions(store);
    }
}
