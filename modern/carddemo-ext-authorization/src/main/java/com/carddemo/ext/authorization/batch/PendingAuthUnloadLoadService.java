package com.carddemo.ext.authorization.batch;

import com.carddemo.ext.authorization.codec.PackedDecimal;
import com.carddemo.ext.authorization.codec.PendingAuthSegments;
import com.carddemo.ext.authorization.config.ConditionalOnAuthorizationExtension;
import com.carddemo.ext.authorization.model.PendingAuthDetail;
import com.carddemo.ext.authorization.model.PendingAuthSummary;
import com.carddemo.ext.authorization.store.PendingAuthDatabase;
import com.carddemo.ext.authorization.store.PendingAuthDatabase.DuplicateSegmentException;
import com.carddemo.ext.authorization.store.PendingAuthDetailRepository;
import com.carddemo.ext.authorization.store.PendingAuthSummaryRepository;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * IMS data migration for DBPAUTP0: PAUDBUNL (unload to root/child sequential files), PAUDBLOD (load
 * from them) and the DFSURGU0 HD unload ({@code IMSDATA.DBPAUTP0.dat}, variable records with a
 * 2-byte length, segment name at offset 10 and segment data at offset 39).
 */
@Service
@ConditionalOnAuthorizationExtension
public class PendingAuthUnloadLoadService {

  /** PAUDBUNL output: roots (LRECL 100) and children (LRECL 206 = ACCNTID COMP-3 + segment). */
  public record Unload(byte[] roots, byte[] children) {}

  /** Counters of a load run (duplicates are skipped as in PAUDBLOD). */
  public record LoadReport(int rootsLoaded, int childrenLoaded, int duplicates, int rejected) {}

  private static final int ROOT_KEY = 6;
  private static final int CHILD_RECORD = ROOT_KEY + PendingAuthSegments.DETAIL_LENGTH;
  private static final int HD_NAME = 10;
  private static final int HD_DATA = 39;

  private final PendingAuthDatabase db;
  private final PendingAuthSummaryRepository summaries;
  private final PendingAuthDetailRepository details;

  public PendingAuthUnloadLoadService(
      PendingAuthDatabase db,
      PendingAuthSummaryRepository summaries,
      PendingAuthDetailRepository details) {
    this.db = db;
    this.summaries = summaries;
    this.details = details;
  }

  @Transactional(readOnly = true)
  public Unload unload(Charset charset) {
    PendingAuthSegments codec = new PendingAuthSegments(charset);
    ByteArrayOutputStream roots = new ByteArrayOutputStream();
    ByteArrayOutputStream children = new ByteArrayOutputStream();
    for (PendingAuthSummary s :
        summaries.findAll(org.springframework.data.domain.Sort.by("acctId"))) {
      byte[] root = codec.encodeSummary(s);
      roots.writeBytes(root);
      for (PendingAuthDetail d :
          details.findByAcctIdOrderByAuthDate9cAscAuthTime9cAsc(s.getAcctId())) {
        children.writeBytes(Arrays.copyOf(root, ROOT_KEY));
        children.writeBytes(codec.encodeDetail(d));
      }
    }
    return new Unload(roots.toByteArray(), children.toByteArray());
  }

  /** PAUDBLOD: ISRT every root, then every child under its root (GE/II tolerated). */
  @Transactional
  public LoadReport load(Unload in, Charset charset) {
    PendingAuthSegments codec = new PendingAuthSegments(charset);
    int roots = 0;
    int kids = 0;
    int dups = 0;
    int rejected = 0;
    for (int o = 0; o + PendingAuthSegments.SUMMARY_LENGTH <= in.roots().length; ) {
      if (!PackedDecimal.isNumeric(in.roots(), o, 11)) {
        rejected++;
      } else if (insert(codec.decodeSummary(in.roots(), o))) {
        roots++;
      } else {
        dups++;
      }
      o += PendingAuthSegments.SUMMARY_LENGTH;
    }
    for (int o = 0; o + CHILD_RECORD <= in.children().length; o += CHILD_RECORD) {
      if (!PackedDecimal.isNumeric(in.children(), o, 11)) {
        rejected++;
        continue;
      }
      long acct = codec.decodeSummary(rootKey(in.children(), o), 0).getAcctId();
      if (db.findFirst(acct).isEmpty()) {
        continue;
      }
      if (insert(codec.decodeDetail(acct, in.children(), o + ROOT_KEY))) {
        kids++;
      } else {
        dups++;
      }
    }
    return new LoadReport(roots, kids, dups, rejected);
  }

  /** Loads the EBCDIC HD unload file of the mainframe (golden {@code IMSDATA.DBPAUTP0.dat}). */
  @Transactional
  public LoadReport loadHdUnload(byte[] dat) {
    return load(fromHdUnload(dat), PendingAuthSegments.EBCDIC);
  }

  /** Converts the HD unload into PAUDBUNL root/child files, still in EBCDIC. */
  public static Unload fromHdUnload(byte[] dat) {
    Charset ebcdic = PendingAuthSegments.EBCDIC;
    ByteArrayOutputStream roots = new ByteArrayOutputStream();
    ByteArrayOutputStream children = new ByteArrayOutputStream();
    byte[] key = new byte[ROOT_KEY];
    int i = 0;
    while (i + 2 <= dat.length) {
      int len = ((dat[i] & 0xFF) << 8) | (dat[i + 1] & 0xFF);
      if (len < 2) {
        break;
      }
      if (len >= HD_DATA + PendingAuthSegments.SUMMARY_LENGTH) {
        String name = new String(dat, i + HD_NAME, 8, ebcdic);
        if ("PAUTSUM0".equals(name)) {
          key = Arrays.copyOfRange(dat, i + HD_DATA, i + HD_DATA + ROOT_KEY);
          roots.write(dat, i + HD_DATA, PendingAuthSegments.SUMMARY_LENGTH);
        } else if ("PAUTDTL1".equals(name) && len >= HD_DATA + PendingAuthSegments.DETAIL_LENGTH) {
          children.writeBytes(key);
          children.write(dat, i + HD_DATA, PendingAuthSegments.DETAIL_LENGTH);
        }
      }
      i += len;
    }
    return new Unload(roots.toByteArray(), children.toByteArray());
  }

  private static byte[] rootKey(byte[] children, int o) {
    byte[] root = new byte[PendingAuthSegments.SUMMARY_LENGTH];
    System.arraycopy(children, o, root, 0, ROOT_KEY);
    return root;
  }

  private boolean insert(PendingAuthSummary s) {
    try {
      db.insert(s);
      return true;
    } catch (DuplicateSegmentException e) {
      return false;
    }
  }

  private boolean insert(PendingAuthDetail d) {
    try {
      db.insert(d);
      return true;
    } catch (DuplicateSegmentException e) {
      return false;
    }
  }
}
