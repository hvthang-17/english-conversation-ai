package vn.englishconversation.ai.service;

import jdk.jfr.*;

/** Opt-in timing only: no owner, request, prompt, SQL or credential fields. */
@Name("vn.englishconversation.Admission")
@Label("AI admission reservation")
@Category("English conversation")
@Enabled(false)
@StackTrace(false)
final class AdmissionEvent extends Event {
  @Timespan(Timespan.NANOSECONDS) public long monitorWaitNanos;
  @Timespan(Timespan.NANOSECONDS) public long connectionWaitNanos;
  @Timespan(Timespan.NANOSECONDS) public long lockWaitNanos;
  @Timespan(Timespan.NANOSECONDS) public long lockHoldNanos;
  @Timespan(Timespan.NANOSECONDS) public long reservationNanos;
  public String outcome = "SUCCESS";
}
