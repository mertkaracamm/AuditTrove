package com.audittrove.llm;

/**
 * Ikincil (capraz kontrol) LLM saglayicisi soyutlamasi.
 * Birincil model OpenAI'dir; bu arayuzu uygulayan beanler dogrulama turunda kullanilir.
 */
public interface SecondaryBackend {
    String name();

    /** API anahtari yapilandirilmis mi? Degilse bean sessizce devre disi kalir. */
    boolean configured();

    /** system + user promptunu calistirir, modelin METIN cevabini dondurur (JSON beklenir). */
    String completeJson(String systemContent, String userContent);
}