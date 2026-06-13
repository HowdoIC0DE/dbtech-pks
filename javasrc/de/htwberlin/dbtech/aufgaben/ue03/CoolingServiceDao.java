package de.htwberlin.dbtech.aufgaben.ue03;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.htwberlin.dbtech.exceptions.CoolingSystemException;
import de.htwberlin.dbtech.exceptions.DataException;

public class CoolingServiceDao implements ICoolingService {

    private static final Logger L = LoggerFactory.getLogger(CoolingServiceDao.class);

    private Connection connection;

    private SampleDao sampleDao;
    private TrayDao trayDao;
    private PlaceDao placeDao;

    @Override
    public void setConnection(Connection connection) {
        this.connection = connection;
        this.sampleDao = new JdbcSampleDao(connection);
        this.trayDao = new JdbcTrayDao(connection);
        this.placeDao = new JdbcPlaceDao(connection);
    }

    private Connection useConnection() {
        if (connection == null) {
            throw new DataException("Connection not set");
        }
        return connection;
    }

    @Override
    public void transferSample(Integer sampleId, Integer diameterInCM) {
        L.info("transferSample DAO: sampleId: " + sampleId + ", diameterInCM: " + diameterInCM);

        useConnection();

        if (!sampleDao.exists(sampleId)) {
            throw new CoolingSystemException("Sample with id " + sampleId + " does not exist");
        }

        if (!trayDao.existsWithDiameter(diameterInCM)) {
            throw new CoolingSystemException("No tray with diameter " + diameterInCM + " exists");
        }

        LocalDate sampleExpirationDate = sampleDao.getExpirationDate(sampleId);

        if (!trayDao.usableTrayExists(diameterInCM, sampleExpirationDate)) {
            throw new CoolingSystemException("No usable tray found");
        }

        Integer trayId = trayDao.findTrayWithExpirationDateAndFreePlace(diameterInCM, sampleExpirationDate);

        if (trayId != null) {
            Integer placeNo = getFirstFreePlaceNo(trayId);
            placeDao.insert(trayId, placeNo, sampleId);
            return;
        }

        trayId = trayDao.findEmptyTrayWithDiameter(diameterInCM);

        if (trayId == null) {
            throw new CoolingSystemException("No empty tray found");
        }

        Integer placeNo = getFirstFreePlaceNo(trayId);
        placeDao.insert(trayId, placeNo, sampleId);
        trayDao.updateExpirationDate(trayId, sampleExpirationDate.plusDays(30));
    }

    private Integer getFirstFreePlaceNo(Integer trayId) {
        Integer capacity = trayDao.getCapacity(trayId);

        for (int placeNo = 1; placeNo <= capacity; placeNo++) {
            if (!placeDao.isOccupied(trayId, placeNo)) {
                return placeNo;
            }
        }

        throw new CoolingSystemException("No free place on tray " + trayId);
    }

    private interface SampleDao {
        boolean exists(Integer sampleId);

        LocalDate getExpirationDate(Integer sampleId);
    }

    private static class JdbcSampleDao implements SampleDao {

        private final Connection connection;

        JdbcSampleDao(Connection connection) {
            this.connection = connection;
        }

        private Connection useConnection() {
            if (connection == null) {
                throw new DataException("Connection not set");
            }
            return connection;
        }

        @Override
        public boolean exists(Integer sampleId) {
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

        @Override
        public LocalDate getExpirationDate(Integer sampleId) {
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
    }

    private interface TrayDao {
        boolean existsWithDiameter(Integer diameterInCM);

        boolean usableTrayExists(Integer diameterInCM, LocalDate sampleExpirationDate);

        Integer findTrayWithExpirationDateAndFreePlace(Integer diameterInCM, LocalDate sampleExpirationDate);

        Integer findEmptyTrayWithDiameter(Integer diameterInCM);

        Integer getCapacity(Integer trayId);

        void updateExpirationDate(Integer trayId, LocalDate expirationDate);
    }

    private static class JdbcTrayDao implements TrayDao {

        private final Connection connection;

        JdbcTrayDao(Connection connection) {
            this.connection = connection;
        }

        private Connection useConnection() {
            if (connection == null) {
                throw new DataException("Connection not set");
            }
            return connection;
        }

        @Override
        public boolean existsWithDiameter(Integer diameterInCM) {
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

        @Override
        public boolean usableTrayExists(Integer diameterInCM, LocalDate sampleExpirationDate) {
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

        @Override
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

        @Override
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

        @Override
        public Integer getCapacity(Integer trayId) {
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

        @Override
        public void updateExpirationDate(Integer trayId, LocalDate expirationDate) {
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
    }

    private interface PlaceDao {
        boolean isOccupied(Integer trayId, Integer placeNo);

        void insert(Integer trayId, Integer placeNo, Integer sampleId);
    }

    private static class JdbcPlaceDao implements PlaceDao {

        private final Connection connection;

        JdbcPlaceDao(Connection connection) {
            this.connection = connection;
        }

        private Connection useConnection() {
            if (connection == null) {
                throw new DataException("Connection not set");
            }
            return connection;
        }

        @Override
        public boolean isOccupied(Integer trayId, Integer placeNo) {
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

        @Override
        public void insert(Integer trayId, Integer placeNo, Integer sampleId) {
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
    }
}