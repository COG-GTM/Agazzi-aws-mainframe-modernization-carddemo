/* DB2 stand-in for COBTUPDT under GnuCOBOL: TRANSACTION_TYPE kept in $TRANTYPE_TBL
 * (2-byte type + 50-byte description per line), FK RESTRICT checked against $TRANCATG_TBL
 * (first 2 bytes = TRAN-TYPE-CD). SQLCODE: 0, +100 not found, -803 duplicate, -532 children. */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define MAXR 1000
static char rows[MAXR][53];
static int n;

static void load(void) {
  FILE *f = fopen(getenv("TRANTYPE_TBL"), "r");
  char line[256];
  n = 0;
  if (!f) return;
  while (fgets(line, sizeof line, f) && n < MAXR) {
    line[strcspn(line, "\r\n")] = 0;
    memset(rows[n], ' ', 52);
    memcpy(rows[n], line, strlen(line) > 52 ? 52 : strlen(line));
    rows[n][52] = 0;
    n++;
  }
  fclose(f);
}

static void save(void) {
  FILE *f = fopen(getenv("TRANTYPE_TBL"), "w");
  for (int i = 0; i < n; i++) fprintf(f, "%s\n", rows[i]);
  fclose(f);
}

static int find(const char *num) {
  for (int i = 0; i < n; i++) if (memcmp(rows[i], num, 2) == 0) return i;
  return -1;
}

static int has_children(const char *num) {
  FILE *f = fopen(getenv("TRANCATG_TBL"), "r");
  char line[256];
  int found = 0;
  if (!f) return 0;
  while (fgets(line, sizeof line, f)) if (memcmp(line, num, 2) == 0) found = 1;
  fclose(f);
  return found;
}

int SQLSTUB(char *op, char *num, char *desc, int *sqlcode) {
  load();
  int i = find(num);
  switch (op[0]) {
    case 'I':
      if (i >= 0) { *sqlcode = -803; return 0; }
      memcpy(rows[n], num, 2); memcpy(rows[n] + 2, desc, 50); rows[n][52] = 0; n++;
      break;
    case 'U':
      if (i < 0) { *sqlcode = 100; return 0; }
      memcpy(rows[i] + 2, desc, 50);
      break;
    case 'D':
      if (i < 0) { *sqlcode = 100; return 0; }
      if (has_children(num)) { *sqlcode = -532; return 0; }
      memmove(rows[i], rows[i + 1], (size_t)(n - i - 1) * sizeof rows[0]); n--;
      break;
  }
  *sqlcode = 0;
  save();
  return 0;
}
