/**
 * Core Minesweeper game model:
 * <ul>
 *   <li>{@link dev.bossiq.minesweeper.model.Board} – generation, reveal/flag, win/lose, save/load</li>
 *   <li>{@link dev.bossiq.minesweeper.model.Cell} - cell state (hidden/revealed/flagged), mine &amp; counts</li>
 *   <li>{@link dev.bossiq.minesweeper.model.Coord} - immutable coordinate (record)</li>
 *   <li>{@link dev.bossiq.minesweeper.model.GameStats} - snapshot of runtime stats</li>
 * </ul>
 * Pure logic, no UI dependencies. Safe for unit tests.
 */
package dev.bossiq.minesweeper.model;
