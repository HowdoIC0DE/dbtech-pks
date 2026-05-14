package de.htwberlin.dbtech.aufgaben.ue02;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.sql.Date;
import de.htwberlin.dbtech.exceptions.CoolingSystemException;
import de.htwberlin.dbtech.exceptions.DataException;
import java.time.LocalDate;

public class CoolingJdbc implements ICoolingJdbc {

    private static final Logger L = LoggerFactory.getLogger
            (CoolingJdbc.class);
    private Connection connection;

    @Override
    public void setConnection(Connection connection) {
        this.connection = connection;
    }

    @SuppressWarnings("unused")
    private Connection useConnection() {
        if (connection == null) {
            throw new DataException("Connection not set");
        }
        return connection;
    }

    @Override
    public List<String> getSampleKinds() {
        L.info("getSampleKinds: start");

        List<String> result = new ArrayList<>();

        String sql = "select Text from SampleKind order by Text";

        try (
                PreparedStatement stmt = useConnection().prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()
        ) {
            while (rs.next()) {
                result.add(rs.getString("Text"));
            }
        } catch (SQLException e) {
            throw new DataException(e);
        }

        return result;
    }

    @Override
    public Sample findSampleById(Integer sampleId) {
        L.info("findSampleById: sampleId: " + sampleId);

        String sql = "select SampleID, SampleKindID, ExpirationDate from Sample where SampleID = ?";

        try (
                PreparedStatement stmt = useConnection().prepareStatement(sql)
        ) {
            stmt.setInt(1, sampleId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Date expirationDate = rs.getDate("ExpirationDate");

                    return new Sample(
                            rs.getInt("SampleID"),
                            rs.getInt("SampleKindID"),
                            expirationDate.toLocalDate()
                    );
                }
            }
        } catch (SQLException e) {
            throw new DataException(e);
        }

        throw new CoolingSystemException("Sample with id " + sampleId + " does not exist");
    }

    @Override
    public void createSample(Integer sampleId, Integer sampleKindId) {
        L.info("createSample: sampleId: " + sampleId + ", sampleKindId: " + sampleKindId);

        // 1. Prüfen, ob SampleID schon existiert
        String checkSampleSql = "select SampleID from Sample where SampleID = ?";

        try (
                PreparedStatement stmt = useConnection().prepareStatement(checkSampleSql)
        ) {
            stmt.setInt(1, sampleId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    throw new CoolingSystemException("Sample with id " + sampleId + " already exists");
                }
            }
        } catch (SQLException e) {
            throw new DataException(e);
        }

        // 2. Prüfen, ob SampleKindID existiert und gültige Tage holen
        Integer validNoOfDays = null;

        String checkSampleKindSql = "select ValidNoOfDays from SampleKind where SampleKindID = ?";

        try (
                PreparedStatement stmt = useConnection().prepareStatement(checkSampleKindSql)
        ) {
            stmt.setInt(1, sampleKindId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    validNoOfDays = rs.getInt("ValidNoOfDays");
                } else {
                    throw new CoolingSystemException("SampleKind with id " + sampleKindId + " does not exist");
                }
            }
        } catch (SQLException e) {
            throw new DataException(e);
        }

        // 3. Ablaufdatum berechnen
        LocalDate expirationDate = LocalDate.now().plusDays(validNoOfDays);

        // 4. Neuen Datensatz einfügen
        String insertSql = "insert into Sample (SampleID, SampleKindID, ExpirationDate) values (?, ?, ?)";

        try (
                PreparedStatement stmt = useConnection().prepareStatement(insertSql)
        ) {
            stmt.setInt(1, sampleId);
            stmt.setInt(2, sampleKindId);
            stmt.setDate(3, Date.valueOf(expirationDate));

            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DataException(e);
        }
    }

    @Override
    public void clearTray(Integer trayId) {
        L.info("clearTray: trayId: " + trayId);

        // 1. Prüfen, ob Tray existiert
        String checkTraySql = "select TrayID from Tray where TrayID = ?";

        try (
                PreparedStatement stmt = useConnection().prepareStatement(checkTraySql)
        ) {
            stmt.setInt(1, trayId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    throw new CoolingSystemException("Tray with id " + trayId + " does not exist");
                }
            }
        } catch (SQLException e) {
            throw new DataException(e);
        }

        // 2. SampleIDs merken, die auf diesem Tray liegen
        List<Integer> sampleIds = new ArrayList<>();

        String selectSamplesSql = "select SampleID from Place where TrayID = ?";

        try (
                PreparedStatement stmt = useConnection().prepareStatement(selectSamplesSql)
        ) {
            stmt.setInt(1, trayId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    sampleIds.add(rs.getInt("SampleID"));
                }
            }
        } catch (SQLException e) {
            throw new DataException(e);
        }

        // 3. Erst Place-Einträge löschen
        String deletePlaceSql = "delete from Place where TrayID = ?";

        try (
                PreparedStatement stmt = useConnection().prepareStatement(deletePlaceSql)
        ) {
            stmt.setInt(1, trayId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DataException(e);
        }

        // 4. Danach die zugehörigen Sample-Einträge löschen
        String deleteSampleSql = "delete from Sample where SampleID = ?";

        try (
                PreparedStatement stmt = useConnection().prepareStatement(deleteSampleSql)
        ) {
            for (Integer sampleId : sampleIds) {
                stmt.setInt(1, sampleId);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new DataException(e);
        }
    }


}
