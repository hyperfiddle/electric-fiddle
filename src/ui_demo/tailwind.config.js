/** @type {import('tailwindcss').Config} */
const defaultTheme = require('tailwindcss/defaultTheme');

module.exports = {
    content:{
        relative: true,
        files: [
            "./**/*.{clj,cljc,cljs}"

            /* FIXME this is not how we want to ship tailwind-style components.
               Good enough for now.
               Instead we want to:
               - ship headless, semantic-only components, consumer will style them
               - ship a stylesheet in hyperfiddle.jar
               - or ship an hyperfiddle tailwind theme */
            , "../../vendor/hyperfiddle/src/**/*.{clj,cljc,cljs}" 
        ]
    },
    theme: {
        extend: {
            fontFamily: {
                sans: ['Inter var', ...defaultTheme.fontFamily.sans],
            },
        },
    },
    plugins: [
        require('@tailwindcss/forms')
    ],
};
