"""Boot failures must not become misleading application-test failures."""
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('boot_gate', Path(__file__).with_name('verify-emulator-boot.py'))
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)


class BootGateTest(unittest.TestCase):
    def test_healthy_empty_buffers(self):
        gate.verify('--------- beginning of events\n', '--------- beginning of crash\n')

    def test_real_system_server_java_fatal_before_r8_install_is_rejected(self):
        with self.assertRaisesRegex(ValueError, 'fatal Java'):
            gate.verify('', '10-20 23:07:28.229 1424 2495 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: ActivityManager\n')

    def test_native_system_server_crash_is_rejected(self):
        with self.assertRaisesRegex(ValueError, 'fatal native'):
            gate.verify('', '10-20 23:07:28.267 1424 2495 F libc    : Fatal signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x10 in tid 2495, pid 1424 (system_server)\n')

    def test_other_process_fatals_are_not_filtered(self):
        with self.assertRaisesRegex(ValueError, 'fatal native'):
            gate.verify('', '10-20 23:07:28.267 1111 2222 F libc: Fatal signal 6 (SIGABRT) in pid 1111 (com.google.android.gms)\n')
        with self.assertRaisesRegex(ValueError, 'fatal Java'):
            gate.verify('', '10-20 23:07:28.229 1111 2222 E AndroidRuntime: FATAL EXCEPTION: main\n')

    def test_recorded_anr_blocks_even_with_healthy_focus(self):
        with self.assertRaisesRegex(ValueError, 'ANR'):
            gate.verify('10-20 23:53:57.581 679 3739 I am_anr  : [0,1464,com.google.android.gms.persistent,-1597325755,Broadcast of Intent]\n', '')

    def test_diagnostic_commands_are_not_crashes(self):
        gate.verify("D adbd: exec logcat '-s' 'am_anr'", "D adbd: grep 'FATAL EXCEPTION' 'Fatal signal'\nE adbd: command requested FATAL EXCEPTION\n")

    def test_large_buffer_does_not_hide_a_fatal(self):
        with self.assertRaisesRegex(ValueError, 'fatal native'):
            gate.verify('', 'D status: ready\n' * 20000 + '10-20 23:07:28.267 1424 2495 F libc: Fatal signal 11 (SIGSEGV)\n')

    def test_runtime_failures_do_not_claim_tests_were_never_started(self):
        for events, crashes in [
            ('I am_anr: [0,1234,com.google.android.gms]', ''),
            ('', 'E AndroidRuntime: FATAL EXCEPTION: main'),
            ('', 'F libc: Fatal signal 6 (SIGABRT) in pid 1534 (hbox:interactor)'),
        ]:
            with self.subTest(events=events, crashes=crashes):
                with self.assertRaisesRegex(ValueError, 'during runtime observation') as result:
                    gate.verify(events, crashes, phase='runtime')
                self.assertNotIn('no tests started', str(result.exception))
                self.assertNotIn('during boot', str(result.exception))

    def test_unknown_phase_cannot_bypass_failure_checks(self):
        with self.assertRaisesRegex(ValueError, 'Unknown observation phase'):
            gate.verify('', '', phase='ignored')


if __name__ == '__main__':
    unittest.main()
