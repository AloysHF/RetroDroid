fn main() {
    // Only the Android build installs the printf-style log shim; host builds
    // (tests, tooling) keep the plain Rust callback.
    if std::env::var("CARGO_CFG_TARGET_OS").as_deref() == Ok("android") {
        cc::Build::new()
            .file("csrc/log_shim.c")
            .compile("retrodroid_log_shim");
    }
}
