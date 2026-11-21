import { mount } from 'svelte'
import '../styles/base.css'
import Overlay from './Overlay.svelte'

mount(Overlay, { target: document.getElementById('app')! })
