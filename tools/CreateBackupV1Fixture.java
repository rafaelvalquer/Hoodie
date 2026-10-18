// Test fixture only. Fixed salt/nonces MUST NOT be used for actual exports.
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import javax.crypto.*;
import javax.crypto.spec.*;

class CreateBackupV1Fixture {
    public static void main(String[] args) throws Exception {
        String datasets = "places,routines,day_exceptions,location_events,context_events,context_confirmations,hoodie_activities,timeline_events,memories,mobility_sessions,mobility_segments,diary_corrections,learned_routine_slots,transport_patterns,transport_evidence,effective_routine_versions";
        String counts = java.util.Arrays.stream(datasets.split(",")).map(n -> "\"" + n + "\":" + (n.equals("places") ? "1" : "0")).collect(java.util.stream.Collectors.joining(","));
        String versions = java.util.Arrays.stream(datasets.split(",")).map(n -> "\"" + n + "\":1").collect(java.util.stream.Collectors.joining(","));
        String[] records = {
            "{\"kind\":\"manifest\",\"formatVersion\":1,\"appVersion\":\"0.2.0-backup-v1-fixture\",\"databaseSchemaVersion\":12,\"createdAt\":1792300000000,\"timezone\":\"UTC\",\"backupId\":\"00000000-0000-4000-8000-000000000001\",\"includeDigital\":false,\"counts\":{" + counts + "},\"datasetVersions\":{" + versions + "}}",
            "{\"kind\":\"settings\",\"version\":1,\"data\":{\"cat_name\":\"Fixture\"}}",
            "{\"kind\":\"record\",\"dataset\":\"places\",\"version\":1,\"data\":{\"id\":7,\"name\":\"Casa fictícia\",\"type\":\"HOME\",\"radiusMeters\":150.0,\"confidence\":0.9,\"isFavorite\":1,\"confirmationCount\":2,\"createdAt\":1700000000000,\"lastVisitedAt\":null,\"latitude\":-23.0,\"longitude\":-46.0}}",
            ""
        };
        byte[] salt = new byte[16], prefix = new byte[8];
        for (int i=0; i<salt.length; i++) salt[i]=(byte)i;
        for (int i=0; i<prefix.length; i++) prefix[i]=(byte)(16+i);
        byte[] header = ByteBuffer.allocate(40).put("HOODIEB1".getBytes(StandardCharsets.US_ASCII)).putInt(1).putInt(600000).put(salt).put(prefix).array();
        var spec = new PBEKeySpec("hoodie recovery phrase".toCharArray(), salt, 600000, 256);
        var key = new SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(), "AES");
        Path path = Path.of("app/src/test/resources/backup-v1-minimal.hoodiebackup");
        try (var out = new DataOutputStream(Files.newOutputStream(path))) {
            out.write(header);
            for (int index=0; index<records.length; index++) {
                byte[] bytes = records[index].getBytes(StandardCharsets.UTF_8);
                var cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, ByteBuffer.allocate(12).put(prefix).putInt(index).array()));
                cipher.updateAAD(header);
                cipher.updateAAD(ByteBuffer.allocate(8).putInt(index).putInt(bytes.length).array());
                out.writeInt(index); out.writeInt(bytes.length); out.write(cipher.doFinal(bytes));
            }
        }
    }
}
