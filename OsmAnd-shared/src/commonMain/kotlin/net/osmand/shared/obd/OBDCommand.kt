package net.osmand.shared.obd

import net.osmand.shared.obd.Obd2Connection.COMMAND_TYPE.*

enum class OBDCommand(
	val commandGroup: Int,
	val command: Int,
	val responseLength: Int,
	private val responseParser: (IntArray) -> OBDDataField<Any>,
	val commandType: Obd2Connection.COMMAND_TYPE = LIVE,
	val isStale: Boolean = false) {
	OBD_CALCULATED_ENGINE_LOAD_COMMAND(0x01, 0x04, 1, OBDUtils::parsePercentResponse),
	OBD_THROTTLE_POSITION_COMMAND(0x01, 0x11, 1, OBDUtils::parsePercentResponse),
	OBD_ENGINE_OIL_TEMPERATURE_COMMAND(0x01, 0x5C, 1, OBDUtils::parseTempResponse),
	OBD_FUEL_PRESSURE_COMMAND(0x01, 0x0A, 1, OBDUtils::parseFuelPressureResponse),
	OBD_BATTERY_VOLTAGE_COMMAND(0x01, 0x42, 2, OBDUtils::parseBatteryVoltageResponse),
	OBD_AMBIENT_AIR_TEMPERATURE_COMMAND(0x01, 0x46, 1, OBDUtils::parseTempResponse),
	OBD_RPM_COMMAND(0x01, 0x0C, 2, OBDUtils::parseRpmResponse),
	OBD_ENGINE_RUNTIME_COMMAND(0x01, 0x1F, 2, OBDUtils::parseEngineRuntime),
	OBD_SPEED_COMMAND(0x01, 0x0D, 1, OBDUtils::parseSpeedResponse),
	OBD_AIR_INTAKE_TEMP_COMMAND(0x01, 0x0F, 1, OBDUtils::parseTempResponse),
	OBD_ENGINE_COOLANT_TEMP_COMMAND(0x01, 0x05, 1, OBDUtils::parseTempResponse),
	OBD_FUEL_CONSUMPTION_RATE_COMMAND(0x01, 0x5E, 2, OBDUtils::parseFuelConsumptionRateResponse),
	OBD_FUEL_TYPE_COMMAND(0x01, 0x51, 1, OBDUtils::parseFuelTypeResponse, isStale = true),
	OBD_VIN_COMMAND(0x09, 0x02, 1, OBDUtils::parseVINResponse, IDENTIFICATION, true),
	OBD_FUEL_LEVEL_COMMAND(0x01, 0x2F, 1, OBDUtils::parsePercentResponse);

	fun parseResponse(response: IntArray): OBDDataField<Any> {
		return responseParser.invoke(response)
	}

	companion object {
		fun getByCode(commandGroup: Int, commandId: Int): OBDCommand? {
			for (command in entries) {
				if (command.commandGroup == commandGroup && command.command == commandId) {
					return command
				}
			}
			return null
		}
	}
}