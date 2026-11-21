#!/usr/bin/env bash
# Cross-compiles Nod.exe for Windows from Linux/WSL with llvm-mingw.
#
# Prerequisites (no root needed):
#   rustup target add x86_64-pc-windows-gnu
#   llvm-mingw extracted to $LLVM_MINGW (default ~/.local/opt/llvm-mingw),
#   from https://github.com/mstorsjo/llvm-mingw/releases (ucrt, ubuntu x86_64).
#
# Output: dist-windows/Nod.exe + WebView2Loader.dll (keep both in the same folder)
# On Windows itself, just run `npm run tauri build` instead (MSVC toolchain + NSIS installer).
set -euo pipefail

cd "$(dirname "$0")/.."
TARGET=x86_64-pc-windows-gnu
LLVM_MINGW=${LLVM_MINGW:-$HOME/.local/opt/llvm-mingw}

if [[ ! -x "$LLVM_MINGW/bin/x86_64-w64-mingw32-clang" ]]; then
  echo "llvm-mingw not found at $LLVM_MINGW (set LLVM_MINGW)" >&2
  exit 1
fi

export PATH="$LLVM_MINGW/bin:$PATH"
export CARGO_TARGET_X86_64_PC_WINDOWS_GNU_LINKER=x86_64-w64-mingw32-clang
export CC_x86_64_pc_windows_gnu=x86_64-w64-mingw32-clang
export CXX_x86_64_pc_windows_gnu=x86_64-w64-mingw32-clang++
export AR_x86_64_pc_windows_gnu=llvm-ar
export RC_x86_64_pc_windows_gnu=llvm-rc
# Rust's windows-gnu target links libgcc/libgcc_eh; llvm-mingw ships the LLVM
# equivalents instead (compiler-rt builtins + libunwind), so alias them.
SHIMS="$PWD/src-tauri/target/mingw-shims"
mkdir -p "$SHIMS"
ln -sf "$(readlink -f "$LLVM_MINGW")/x86_64-w64-mingw32/lib/libunwind.a" "$SHIMS/libgcc_eh.a"
ln -sf "$(echo "$(readlink -f "$LLVM_MINGW")"/lib/clang/*/lib/windows/libclang_rt.builtins-x86_64.a)" "$SHIMS/libgcc.a"
export CARGO_TARGET_X86_64_PC_WINDOWS_GNU_RUSTFLAGS="-L native=$SHIMS"

# Keep memory usage friendly on laptops / WSL.
export CARGO_BUILD_JOBS=${CARGO_BUILD_JOBS:-8}

# --no-bundle: the NSIS installer can only be produced on Windows.
npx tauri build --target "$TARGET" --no-bundle "$@"

mkdir -p dist-windows
cp "src-tauri/target/$TARGET/release/nod-desktop.exe" dist-windows/Nod.exe
# The GNU target loads WebView2 through this small loader DLL; ship it next to the exe.
cp "src-tauri/target/$TARGET/release/WebView2Loader.dll" dist-windows/
echo "Built dist-windows/Nod.exe (+ WebView2Loader.dll)"
