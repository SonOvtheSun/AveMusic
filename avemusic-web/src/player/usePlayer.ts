import { useContext } from "react";

import {
    PlayerContext,
    type PlayerContextValue,
} from "./PlayerContext";

export function usePlayer():
        PlayerContextValue {
    const context =
        useContext(PlayerContext);

    if (context === null) {
        throw new Error(
            "usePlayer 必须在 PlayerProvider 内使用",
        );
    }

    return context;
}
