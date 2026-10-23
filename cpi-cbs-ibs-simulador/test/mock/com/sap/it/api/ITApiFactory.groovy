package com.sap.it.api

import com.sap.it.api.mapping.ValueMappingApi

/** Mock local do ITApiFactory do SAP Cloud Integration. */
class ITApiFactory {
    static <T> T getApi(Class<T> tipo, Object contexto) {
        if (tipo == ValueMappingApi) return (T) new ValueMappingApi()
        throw new IllegalArgumentException("API não suportada no mock: ${tipo}")
    }
}
