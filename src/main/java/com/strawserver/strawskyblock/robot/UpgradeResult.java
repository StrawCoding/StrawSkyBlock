package com.strawserver.strawskyblock.robot;

/**
 * 機器人等級升級驗證結果。
 */
public enum UpgradeResult {

    /** 可以升級。 */
    OK,
    /** 目標等級超出有效範圍（1 ~ maxLevel）。 */
    OUT_OF_RANGE,
    /** 目標等級不高於目前等級（不允許降級或維持原級）。 */
    NOT_HIGHER,
    /** 已達最高等級。 */
    ALREADY_MAX
}
