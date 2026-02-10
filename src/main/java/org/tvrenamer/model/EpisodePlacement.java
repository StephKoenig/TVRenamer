package org.tvrenamer.model;

import org.tvrenamer.controller.util.StringUtils;

public record EpisodePlacement(int season, int episode) {

    @Override
    public String toString() {
        return "S" + StringUtils.zeroPadTwoDigits(season)
            + "E" + StringUtils.zeroPadTwoDigits(episode);
    }
}
