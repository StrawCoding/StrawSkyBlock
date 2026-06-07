package com.strawserver.strawskyblock.database;

import com.strawserver.strawskyblock.config.ConfigManager;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;

/**
 * 建立 HikariCP 連線池。
 */
public class MySQLProvider {

    private HikariDataSource dataSource;

    public DataSource init(ConfigManager config) {
        HikariConfig hikari = new HikariConfig();
        String jdbc = "jdbc:mysql://" + config.getDbHost() + ":" + config.getDbPort()
                + "/" + config.getDbName()
                + "?useUnicode=true&characterEncoding=utf8"
                + "&useSSL=" + config.isDbUseSsl()
                + "&allowPublicKeyRetrieval=true"
                + "&serverTimezone=UTC";
        hikari.setJdbcUrl(jdbc);
        hikari.setUsername(config.getDbUser());
        hikari.setPassword(config.getDbPassword());
        hikari.setDriverClassName("com.mysql.cj.jdbc.Driver");
        hikari.setMaximumPoolSize(config.getDbPoolSize());
        hikari.setPoolName("StrawSkyBlockPool");
        hikari.setConnectionTimeout(10_000L);
        hikari.setMaxLifetime(1_800_000L);
        hikari.setKeepaliveTime(60_000L);
        hikari.addDataSourceProperty("cachePrepStmts", "true");
        hikari.addDataSourceProperty("prepStmtCacheSize", "250");
        hikari.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        this.dataSource = new HikariDataSource(hikari);
        return this.dataSource;
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
