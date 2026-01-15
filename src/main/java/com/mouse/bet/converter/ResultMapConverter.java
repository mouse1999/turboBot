package com.mouse.bet.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mouse.bet.enums.BookMaker;
import com.mouse.bet.orchestrator.model.LegResult;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Converter
public class ResultMapConverter implements AttributeConverter<Map<BookMaker, LegResult>, String> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    static {
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public String convertToDatabaseColumn(Map<BookMaker, LegResult> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            log.error("Error converting ResultMap to JSON", e);
            throw new IllegalArgumentException("Error converting map to JSON", e);
        }
    }

    @Override
    public Map<BookMaker, LegResult> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.trim().isEmpty()) {
            return new HashMap<>();
        }

        try {
            TypeReference<Map<BookMaker, LegResult>> typeRef =
                    new TypeReference<Map<BookMaker, LegResult>>() {};
            return objectMapper.readValue(dbData, typeRef);
        } catch (JsonProcessingException e) {
            log.error("Error converting JSON to ResultMap: {}", dbData, e);
            return new HashMap<>(); // Return empty map instead of throwing
        }
    }
}