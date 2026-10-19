package com.carddemo.online.transaction.service;

import com.carddemo.contracts.port.BusinessClock;
import com.carddemo.contracts.screen.ConversationContext;
import com.carddemo.contracts.screen.FieldError;
import com.carddemo.contracts.screen.ScreenId;
import com.carddemo.contracts.screen.ScreenMessage;
import com.carddemo.contracts.screen.ScreenResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Working storage of one task: the symbolic map ({@code <map>I}/{@code <map>O} share storage),
 * {@code WS-MESSAGE}, {@code WS-ERR-FLG}, the cursor ({@code MOVE -1 TO <field>L}) and the colour
 * of ERRMSG. {@link #send} snapshots what SEND MAP puts on the terminal: the last SEND of the task
 * is the screen the user sees. Programs whose SEND paragraph ends with {@code EXEC CICS RETURN}
 * (COTRN02C, CORPT00C) are built with {@code returnAfterSend}, so the first SEND ends the task.
 */
public final class MapBuffer {

  /** {@code EXEC CICS RETURN} issued inside the SEND paragraph. */
  public static final class ReturnToCics extends RuntimeException {
    private static final long serialVersionUID = 1L;

    ReturnToCics() {
      super(null, null, false, false);
    }
  }

  private static final DateTimeFormatter CURDATE = DateTimeFormatter.ofPattern("MM/dd/yy");
  private static final DateTimeFormatter CURTIME = DateTimeFormatter.ofPattern("HH:mm:ss");

  /** COTTL01Y CCDA-TITLE01/02. */
  static final String TITLE01 = "      AWS Mainframe Modernization       ";

  static final String TITLE02 = "  Credit Card Demo Application (CCDA)   ";

  /** CSMSG01Y CCDA-MSG-INVALID-KEY. */
  public static final String MSG_INVALID_KEY = "Invalid key pressed. Please see below...         ";

  private final ScreenId screen;
  private final Map<String, Integer> lengths;
  private final BusinessClock clock;
  private final boolean returnAfterSend;
  private final Map<String, String> fields = new LinkedHashMap<>();
  private String message = "";
  private boolean green;
  private boolean err;
  private String cursor;

  private Map<String, String> shown;
  private String sentMessage = "";
  private boolean sentGreen;
  private FieldError sentError;

  public MapBuffer(
      ScreenId screen,
      Class<? extends Record> output,
      BusinessClock clock,
      boolean returnAfterSend) {
    this.screen = screen;
    this.lengths = BmsMaps.lengths(output);
    this.clock = clock;
    this.returnAfterSend = returnAfterSend;
  }

  public String get(String field) {
    return fields.getOrDefault(field, "");
  }

  /** MOVE to the map field (truncated to the BMS LENGTH). */
  public void set(String field, String value) {
    Integer len = lengths.get(field);
    if (len == null) {
      throw new IllegalArgumentException(screen + " has no field " + field);
    }
    String v = value == null ? "" : value;
    fields.put(field, v.length() > len ? v.substring(0, len) : v);
  }

  /** MOVE SPACES TO each field. */
  public void spaces(String... names) {
    for (String n : names) {
      set(n, " ".repeat(lengths.get(n)));
    }
  }

  /** MOVE LOW-VALUES TO the whole map. */
  public void lowValues() {
    fields.clear();
  }

  /** RECEIVE MAP. */
  public void receive(Map<String, String> input) {
    if (input != null) {
      input.forEach(this::set);
    }
  }

  public String message() {
    return message;
  }

  public void message(String text) {
    this.message = text == null ? "" : text;
  }

  public void green() {
    this.green = true;
  }

  public void cursor(String field) {
    this.cursor = field;
  }

  public boolean err() {
    return err;
  }

  public void err(boolean on) {
    this.err = on;
  }

  /** Error helper: {@code MOVE 'Y' TO WS-ERR-FLG}, message, cursor. */
  public void error(String text, String cursorField) {
    err = true;
    message(text);
    cursor(cursorField);
  }

  /** SEND MAP ... ERASE. */
  public void send() {
    send(true, Map.of());
  }

  /**
   * SEND MAP. Without ERASE, fields at LOW-VALUES are not transmitted and the terminal keeps {@code
   * previous}.
   */
  public void send(boolean erase, Map<String, String> previous) {
    populateHeader();
    Map<String, String> out = new LinkedHashMap<>();
    if (!erase && previous != null) {
      out.putAll(previous);
    }
    out.putAll(fields);
    String m = message == null ? "" : message;
    out.put("ERRMSG", m.length() > lengths.get("ERRMSG") ? m.substring(0, 78) : m);
    shown = out;
    sentMessage = m;
    sentGreen = green;
    sentError =
        err && cursor != null && !CobolFmt.blank(m) ? new FieldError(cursor, m.strip()) : null;
    if (returnAfterSend) {
      throw new ReturnToCics();
    }
  }

  /** POPULATE-HEADER-INFO. */
  private void populateHeader() {
    LocalDateTime now = clock.now();
    set("TITLE01", TITLE01);
    set("TITLE02", TITLE02);
    set("TRNNAME", screen.transactionId());
    set("PGMNAME", screen.program());
    set("CURDATE", CURDATE.format(now));
    set("CURTIME", CURTIME.format(now));
  }

  /** Fields displayed by the last SEND (empty if nothing was sent). */
  public Map<String, String> shown() {
    return shown == null ? Map.of() : shown;
  }

  public boolean sent() {
    return shown != null;
  }

  /** The terminal after the task: last SEND, its message, cursor error and COMMAREA. */
  public <O extends Record> ScreenResponse<O> response(
      Class<O> output, ConversationContext context) {
    O data = BmsMaps.build(output, shown());
    List<ScreenMessage> messages =
        CobolFmt.blank(sentMessage)
            ? List.of()
            : List.of(
                sentGreen
                    ? ScreenMessage.info(sentMessage.strip())
                    : ScreenMessage.error(sentMessage.strip()));
    return new ScreenResponse<>(
        screen, data, messages, null, sentError == null ? List.of() : List.of(sentError), context);
  }

  /** XCTL: no map is sent by this program, the client moves to {@code next}. */
  public static <O> ScreenResponse<O> xctl(
      ScreenId screen, ScreenId next, ConversationContext context) {
    return new ScreenResponse<>(screen, null, List.of(), next, List.of(), context);
  }
}
