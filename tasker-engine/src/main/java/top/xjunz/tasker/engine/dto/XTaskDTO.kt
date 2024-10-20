/*
 * Copyright (c) 2023 xjunz. All rights reserved.
 */

package top.xjunz.tasker.engine.dto

import android.os.Parcel
import android.os.Parcelable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import top.xjunz.shared.ktx.requireParcelable
import top.xjunz.tasker.engine.applet.base.RootFlow
import top.xjunz.tasker.engine.applet.factory.AppletFactory
import top.xjunz.tasker.engine.dto.AppletChecksum.calculateChecksum
import top.xjunz.tasker.engine.dto.AppletDTO.Serializer.toDTO
import top.xjunz.tasker.engine.task.XTask

/**
 * Data Transfer Object for [XTask].
 *
 * @author xjunz 2022/12/16
 */
@Serializable
@SerialName("T")
class XTaskDTO(
    @SerialName("f") val flow: AppletDTO,
    @SerialName("m") val metadata: XTask.Metadata
) : Parcelable {

    constructor(parcel: Parcel) : this(parcel.requireParcelable(), parcel.requireParcelable())

    fun verifyChecksum(): Boolean {
        return metadata.checksum ==  metadata.checksum
    }

    fun toXTask(factory: AppletFactory, compatMode: Boolean): XTask {
        val task = XTask()
        task.flow = flow.toApplet(factory, compatMode) as RootFlow
        task.metadata = metadata
        return task
    }

    fun toXTaskPrevVersionCode16(factory: AppletFactory): XTask {
        val task = XTask()
        task.flow = flow.toAppletPreVersionCode16(factory) as RootFlow
        task.metadata = metadata
        return task
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeParcelable(flow, flags)
        parcel.writeParcelable(metadata, flags)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<XTaskDTO> {
        override fun createFromParcel(parcel: Parcel): XTaskDTO {
            return XTaskDTO(parcel)
        }

        override fun newArray(size: Int): Array<XTaskDTO?> {
            return arrayOfNulls(size)
        }
    }

}

fun XTask.toDTO(): XTaskDTO {
    return XTaskDTO(requireFlow().toDTO(), metadata)
}

val XTaskJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
}
