#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>

typedef void (*retrodroid_log_sink)(uint32_t level, const char *text);

static retrodroid_log_sink sink = NULL;

void retrodroid_log_set_sink(retrodroid_log_sink callback) { sink = callback; }

/* The libretro log interface is printf-style; cores pass "%s" plus the text,
 * so the arguments are formatted here before crossing back into Rust. */
void retrodroid_log_printf(uint32_t level, const char *fmt, ...) {
  char buffer[2048];
  va_list args;
  va_start(args, fmt);
  vsnprintf(buffer, sizeof(buffer), fmt, args);
  va_end(args);
  if (sink != NULL) {
    sink(level, buffer);
  }
}
