package com.audittrove.audit;

/** Kullanıcı incelemeyi iptal etti; kalan model çağrıları yapılmaz. Hata değil, akışı kesme yolu. */
public class AuditCancelledException extends RuntimeException {
    public AuditCancelledException() {
        super("İnceleme iptal edildi");
    }
}
