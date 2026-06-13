package de.htwberlin.dbtech.aufgaben.ue03;

import java.sql.Connection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import de.htwberlin.dbtech.exceptions.CoolingSystemException;
import de.htwberlin.dbtech.exceptions.DataException;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class CoolingService implements ICoolingService {
    private static final Logger L = LoggerFactory.getLogger(CoolingService.class);
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

    public boolean isSampleIdExisting(Integer sampleId) {
        String sql = "select count(SampleID) as Anzahl from Sample where SampleID = ?";

        try (
                PreparedStatement stmt = useConnection().prepareStatement(sql)
        ) {
            stmt.setInt(1, sampleId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("Anzahl") > 0;
                }
            }
        } catch (SQLException e) {
            throw new DataException(e);
        }

        return false;
    }

    public boolean isTrayWithDiameterExisting(Integer diameterInCM) {
        String sql = "select count(TrayID) as Anzahl from Tray where DiameterInCM = ?";

        try (
                PreparedStatement stmt = useConnection().prepareStatement(sql)
        ) {
            stmt.setInt(1, diameterInCM);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("Anzahl") > 0;
                }
            }
        } catch (SQLException e) {
            throw new DataException(e);
        }

        return false;
    }

    public LocalDate getSampleExpirationDate(Integer sampleId) {
        String sql = "select ExpirationDate from Sample where SampleID = ?";

        try (
                PreparedStatement stmt = useConnection().prepareStatement(sql)
        ) {
            stmt.setInt(1, sampleId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Date expirationDate = rs.getDate("ExpirationDate");
                    return expirationDate.toLocalDate();
                }
            }
        } catch (SQLException e) {
            throw new DataException(e);
        }

        throw new CoolingSystemException("Sample with id " + sampleId + " does not exist");
    }

    public boolean isUsableTrayExisting(Integer diameterInCM, LocalDate sampleExpirationDate) {
        String sql =
                "select count(t.TrayID) as Anzahl " +
                        "from Tray t " +
                        "where t.DiameterInCM = ? " +
                        "and ( " +
                        "    (t.ExpirationDate is not null " +
                        "     and t.ExpirationDate > ? " +
                        "     and (select count(*) from Place p where p.TrayID = t.TrayID) < t.Capacity) " +
                        "    or " +
                        "    (t.ExpirationDate is null " +
                        "     and (select count(*) from Place p where p.TrayID = t.TrayID) = 0) " +
                        ")";

        try (
                PreparedStatement stmt = useConnection().prepareStatement(sql)
        ) {
            stmt.setInt(1, diameterInCM);
            stmt.setDate(2, Date.valueOf(sampleExpirationDate));

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("Anzahl") > 0;
                }
            }
        } catch (SQLException e) {
            throw new DataException(e);
        }

        return false;
    }

    public Integer findTrayWithExpirationDateAndFreePlace(Integer diameterInCM, LocalDate sampleExpirationDate) {
        String sql =
                "select t.TrayID " +
                        "from Tray t " +
                        "where t.DiameterInCM = ? " +
                        "and t.ExpirationDate is not null " +
                        "and t.ExpirationDate > ? " +
                        "and (select count(*) from Place p where p.TrayID = t.TrayID) < t.Capacity " +
                        "order by t.ExpirationDate, t.TrayID";

        try (
                PreparedStatement stmt = useConnection().prepareStatement(sql)
        ) {
            stmt.setInt(1, diameterInCM);
            stmt.setDate(2, Date.valueOf(sampleExpirationDate));

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("TrayID");
                }
            }
        } catch (SQLException e) {
            throw new DataException(e);
        }

        return null;
    }

    public Integer findEmptyTrayWithDiameter(Integer diameterInCM) {
        String sql =
                "select t.TrayID " +
                        "from Tray t " +
                        "where t.DiameterInCM = ? " +
                        "and t.ExpirationDate is null " +
                        "and not exists (select 1 from Place p where p.TrayID = t.TrayID) " +
                        "order by t.TrayID";

        try (
                PreparedStatement stmt = useConnection().prepareStatement(sql)
        ) {
            stmt.setInt(1, diameterInCM);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("TrayID");
                }
            }
        } catch (SQLException e) {
            throw new DataException(e);
        }

        return null;
    }

    public Integer getTrayCapacity(Integer trayId) {
        String sql = "select Capacity from Tray where TrayID = ?";

        try (
                PreparedStatement stmt = useConnection().prepareStatement(sql)
        ) {
            stmt.setInt(1, trayId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("Capacity");
                }
            }
        } catch (SQLException e) {
            throw new DataException(e);
        }

        throw new CoolingSystemException("Tray with id " + trayId + " does not exist");
    }

    public boolean isPlaceOccupied(Integer trayId, Integer placeNo) {
        String sql = "select count(*) as Anzahl from Place where TrayID = ? and PlaceNo = ?";

        try (
                PreparedStatement stmt = useConnection().prepareStatement(sql)
        ) {
            stmt.setInt(1, trayId);
            stmt.setInt(2, placeNo);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("Anzahl") > 0;
                }
            }
        } catch (SQLException e) {
            throw new DataException(e);
        }

        return false;
    }

    public Integer getFirstFreePlaceNo(Integer trayId) {
        Integer capacity = getTrayCapacity(trayId);

        for (int placeNo = 1; placeNo <= capacity; placeNo++) {
            if (!isPlaceOccupied(trayId, placeNo)) {
                return placeNo;
            }
        }

        throw new CoolingSystemException("No free place on tray " + trayId);
    }

    public void insertPlace(Integer trayId, Integer placeNo, Integer sampleId) {
        String sql = "insert into Place (TrayID, PlaceNo, SampleID) values (?, ?, ?)";

        try (
                PreparedStatement stmt = useConnection().prepareStatement(sql)
        ) {
            stmt.setInt(1, trayId);
            stmt.setInt(2, placeNo);
            stmt.setInt(3, sampleId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DataException(e);
        }
    }

    public void updateTrayExpirationDate(Integer trayId, LocalDate expirationDate) {
        String sql = "update Tray set ExpirationDate = ? where TrayID = ?";

        try (
                PreparedStatement stmt = useConnection().prepareStatement(sql)
        ) {
            stmt.setDate(1, Date.valueOf(expirationDate));
            stmt.setInt(2, trayId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DataException(e);
        }
    }

    @Override
    public void transferSample(Integer sampleId, Integer diameterInCM) {
        L.info("transferSample: sampleId: " + sampleId + ", diameterInCM: " + diameterInCM);
        // 1. Existiert die Probe?
        if (!isSampleIdExisting(sampleId)) {
            throw new CoolingSystemException("Sample with id " + sampleId + " does not exist");
        }
        // 2. Gibt es ein Tablett mit passendem Durchmesser?
        if (!isTrayWithDiameterExisting(diameterInCM)) {
            throw new CoolingSystemException("No tray with diameter " + diameterInCM + " exists");
        }
        // 3. Gibt es ein geeignetes Tablett mit freiem Platz?
        LocalDate sampleExpirationDate = getSampleExpirationDate(sampleId);

        if (!isUsableTrayExisting(diameterInCM, sampleExpirationDate)) {
            throw new CoolingSystemException("No usable tray found");
        }
        // 4. Zuerst Tablett mit gesetztem Ablaufdatum suchen
        Integer trayId = findTrayWithExpirationDateAndFreePlace(diameterInCM, sampleExpirationDate);

        if (trayId != null) {
            Integer placeNo = getFirstFreePlaceNo(trayId);
            insertPlace(trayId, placeNo, sampleId);
            return;
        }

        // 5. Sonst leeres Tablett mit passendem Durchmesser nehmen
        trayId = findEmptyTrayWithDiameter(diameterInCM);

        if (trayId == null) {
            throw new CoolingSystemException("No empty tray found");
        }
        Integer placeNo = getFirstFreePlaceNo(trayId);
        insertPlace(trayId, placeNo, sampleId);

        // Ablaufdatum des neuen Tabletts setzen: Ablaufdatum Probe + 30 Tage
        updateTrayExpirationDate(trayId, sampleExpirationDate.plusDays(30));


    }

}
