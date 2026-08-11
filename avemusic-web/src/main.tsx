import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";

import App from "./App";

const rootElement =
    document.getElementById("root");

if (rootElement === null) {
    throw new Error(
        "index.html 中缺少 id=root 的节点",
    );
}

createRoot(rootElement).render(
    <StrictMode>
        {/*
         * 整个项目只在这里创建一次 BrowserRouter。
         * App.tsx 中不要再次创建 BrowserRouter。
         */}
        <BrowserRouter>
            <App />
        </BrowserRouter>
    </StrictMode>,
);
