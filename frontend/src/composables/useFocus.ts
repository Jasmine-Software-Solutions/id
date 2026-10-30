export function useFocus() {
    const vAutoFocus = {
        mounted: (el: any) => el.focus()
    }

    return { vAutoFocus }
}
