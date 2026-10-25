package lk.bookhive.member.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lk.bookhive.common.crypto.FieldEncryption;
import org.springframework.stereotype.Component;

/**
 * Encrypts the NIC on the way into the database and decrypts it on the way out (checklist step
 * 128), so the rest of the code just sees a String. Spring creates it, so the key can be injected.
 */
@Component
@Converter
public class NicConverter implements AttributeConverter<String, String> {

  private final FieldEncryption encryption;

  public NicConverter(FieldEncryption encryption) {
    this.encryption = encryption;
  }

  @Override
  public String convertToDatabaseColumn(String nic) {
    return encryption.encrypt(nic);
  }

  @Override
  public String convertToEntityAttribute(String stored) {
    return encryption.decrypt(stored);
  }
}
