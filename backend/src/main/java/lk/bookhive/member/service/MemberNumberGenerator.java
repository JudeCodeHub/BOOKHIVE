package lk.bookhive.member.service;

import lk.bookhive.member.repository.MemberRepository;
import org.springframework.stereotype.Component;

/**
 * Human-friendly member numbers for the card and the desk: BH-000001, BH-000002... (MEM-4). Backed
 * by a database sequence, so two approvals at the same moment never get the same number.
 */
@Component
public class MemberNumberGenerator {

  private static final String PREFIX = "BH-";

  private final MemberRepository members;

  public MemberNumberGenerator(MemberRepository members) {
    this.members = members;
  }

  public String next() {
    return format(members.nextMemberNumber());
  }

  /** 123 -> "BH-000123". Past 999,999 it simply grows a digit: "BH-1000000". */
  static String format(long number) {
    return PREFIX + String.format("%06d", number);
  }
}
