package com.popcorn.coupon.domain.entity.converter

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

@Converter
class JsonNodeConverter : AttributeConverter<JsonNode?, String?> {

    private val objectMapper = ObjectMapper()

    override fun convertToDatabaseColumn(attribute: JsonNode?): String? {
        return attribute?.let { objectMapper.writeValueAsString(it) }
    }

    override fun convertToEntityAttribute(dbData: String?): JsonNode? {
        return dbData?.let { objectMapper.readTree(it) }
    }
}