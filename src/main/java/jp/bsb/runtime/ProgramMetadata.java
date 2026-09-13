package jp.bsb.runtime;

import java.util.Objects;
import java.util.Optional;

/** 埋込みホストが提供する、未検証の論理名と任意の場所URIです。 */
public record ProgramMetadata(String name, Optional<String> location) {
  /** nullを許さず、値の妥当性検査は利用時まで遅延します。 */
  public ProgramMetadata {
    Objects.requireNonNull(name, "name");
    location = Objects.requireNonNull(location, "location");
  }

  /** 場所を持つメタデータを作ります。 */
  public static ProgramMetadata located(String name, String location) {
    return new ProgramMetadata(name, Optional.of(Objects.requireNonNull(location, "location")));
  }

  /** 場所を持たない埋込み用メタデータを作ります。 */
  public static ProgramMetadata unlocated(String name) {
    return new ProgramMetadata(name, Optional.empty());
  }
}
